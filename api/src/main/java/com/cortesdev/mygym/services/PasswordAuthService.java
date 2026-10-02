package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.AuthToken;
import com.cortesdev.mygym.models.AuthTokenPurpose;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.LoginResponse;
import com.cortesdev.mygym.models.dto.TokenInfoResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.AuthTokenRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.security.JwtService;
import com.cortesdev.mygym.services.exception.InvalidCredentialsException;
import com.cortesdev.mygym.services.exception.InvalidOrExpiredTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email + contraseña como alternativa a Google (ver GoogleTokenVerifier/AuthService) — convive
 * con Google en la misma AppUser, matcheada siempre por email. Las 3 variantes (invitado por
 * admin, alta pública sin Google, "olvidé mi contraseña") comparten un único token de un solo
 * uso (ver AuthToken/AuthTokenPurpose) que converge siempre en el mismo paso final: setear/crear
 * un passwordHash y emitir el mismo JWT que issueToken ya usa para Google.
 *
 * <p>Deliberadamente genérico contra enumeración de cuentas: requestSelfRegistration y
 * requestPasswordReset nunca revelan si un email existe o no — el controller siempre devuelve el
 * mismo mensaje, pase lo que pase acá adentro.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordAuthService {

    private static final Logger log = LoggerFactory.getLogger(PasswordAuthService.class);

    private static final Duration INVITE_TTL = Duration.ofHours(48);
    private static final Duration SELF_REGISTER_TTL = Duration.ofHours(48);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final String ACTIVATION_BASE_URL = "https://www.mygym.cl/activar/";

    private final AppUserRepository appUserRepository;
    private final AuthTokenRepository authTokenRepository;
    private final GymRepository gymRepository;
    private final JwtService jwtService;
    private final MemberLifecycleEmailService memberLifecycleEmailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginResponse login(String email, String rawPassword) {
        AppUser user = appUserRepository
                .findByEmail(AppUser.normalizeEmail(email))
                .orElseThrow(InvalidCredentialsException::new);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }
        return issueLoginResponse(user, false);
    }

    /**
     * Nunca lanza una excepción que distinga "el email ya existe" de "se mandó bien" — el
     * controller siempre responde el mismo mensaje genérico. Si el gym no existe/está inactivo, o
     * el email ya tiene cuenta, esto simplemente no manda nada (silencioso a propósito).
     */
    public void requestSelfRegistration(String name, String email, String gymSlug) {
        Gym gym = gymRepository.findBySlug(gymSlug).filter(Gym::isActive).orElse(null);
        if (gym == null) {
            log.info("Auto-registro con contraseña ignorado: gym '{}' no existe o está inactivo", gymSlug);
            return;
        }
        String normalized = AppUser.normalizeEmail(email);
        if (appUserRepository.existsByEmail(normalized)) {
            log.info("Auto-registro con contraseña ignorado: ya existe una cuenta para ese email");
            return;
        }
        String rawToken = createToken(builder -> builder
                .purpose(AuthTokenPurpose.SELF_REGISTER)
                .pendingName(name)
                .pendingEmail(normalized)
                .pendingGymId(gym.getId()),
                SELF_REGISTER_TTL);
        memberLifecycleEmailService.sendSelfRegisterActivation(gym, name, normalized, activationUrl(rawToken));
    }

    public void requestPasswordReset(String email) {
        AppUser user = appUserRepository.findByEmail(AppUser.normalizeEmail(email)).orElse(null);
        if (user == null || !user.isActive()) {
            log.info("Reset de contraseña ignorado: cuenta inexistente o inactiva");
            return;
        }
        String rawToken = createToken(
                builder -> builder.purpose(AuthTokenPurpose.PASSWORD_RESET).appUserId(user.getId()),
                PASSWORD_RESET_TTL);
        Gym gym = user.getGymId() != null ? gymRepository.findById(user.getGymId()).orElse(null) : null;
        memberLifecycleEmailService.sendPasswordReset(gym, user, activationUrl(rawToken));
    }

    @Transactional(readOnly = true)
    public TokenInfoResponse tokenInfo(String rawToken) {
        AuthToken token = findUsableToken(rawToken);
        if (token.getPurpose() == AuthTokenPurpose.SELF_REGISTER) {
            return new TokenInfoResponse(token.getPurpose(), token.getPendingName(), token.getPendingEmail());
        }
        AppUser user = appUserRepository.findById(token.getAppUserId()).orElseThrow(InvalidOrExpiredTokenException::new);
        return new TokenInfoResponse(token.getPurpose(), user.getName(), user.getEmail());
    }

    public LoginResponse activate(String rawToken, String rawPassword) {
        AuthToken token = findUsableToken(rawToken);
        String hash = passwordEncoder.encode(rawPassword);

        AppUser user;
        boolean isNewMember = false;
        if (token.getPurpose() == AuthTokenPurpose.SELF_REGISTER) {
            if (appUserRepository.existsByEmail(token.getPendingEmail())) {
                // Carrera: alguien más tomó este email (ej. Google) entre el alta y la activación.
                throw new InvalidOrExpiredTokenException();
            }
            Gym gym = gymRepository.findById(token.getPendingGymId()).orElseThrow(InvalidOrExpiredTokenException::new);
            user = appUserRepository.save(AppUser.builder()
                    .email(token.getPendingEmail())
                    .name(token.getPendingName())
                    .role(Role.MEMBER)
                    .gymId(gym.getId())
                    .active(true)
                    .passwordHash(hash)
                    .build());
            isNewMember = true;
            memberLifecycleEmailService.sendMemberWelcome(gym, user);
            List<String> adminEmails = appUserRepository.findByGymIdAndRole(gym.getId(), Role.GYM_ADMIN).stream()
                    .map(AppUser::getEmail)
                    .toList();
            memberLifecycleEmailService.sendNewMemberNotice(gym, user, adminEmails);
        } else {
            user = appUserRepository.findById(token.getAppUserId()).orElseThrow(InvalidOrExpiredTokenException::new);
            user.setPasswordHash(hash);
            appUserRepository.save(user);
        }

        token.setUsedAt(Instant.now());
        authTokenRepository.save(token);
        return issueLoginResponse(user, isNewMember);
    }

    /** Usado por MemberService/MemberImportRowService al invitar a un socio a mano — el email de
     *  invitación ya existente sigue apuntando a /j/{slug} (Google) como opción principal; esto
     *  solo agrega la URL de una segunda opción (contraseña) en el mismo correo. */
    public String createInviteToken(AppUser member) {
        return createToken(builder -> builder.purpose(AuthTokenPurpose.INVITE).appUserId(member.getId()), INVITE_TTL);
    }

    public String activationUrl(String rawToken) {
        return ACTIVATION_BASE_URL + rawToken;
    }

    private LoginResponse issueLoginResponse(AppUser user, boolean isNewMember) {
        user.setLastLoginAt(Instant.now());
        appUserRepository.save(user);
        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getRole(), user.getGymId());
        return new LoginResponse(
                token, user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getGymId(), isNewMember,
                user.getPhotoUrl());
    }

    private AuthToken findUsableToken(String rawToken) {
        AuthToken token = authTokenRepository
                .findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidOrExpiredTokenException::new);
        if (!token.isUsable()) {
            throw new InvalidOrExpiredTokenException();
        }
        return token;
    }

    private String createToken(java.util.function.Consumer<AuthToken.AuthTokenBuilder> customize, Duration ttl) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AuthToken.AuthTokenBuilder builder = AuthToken.builder().tokenHash(hash(rawToken)).expiresAt(Instant.now().plus(ttl));
        customize.accept(builder);
        authTokenRepository.save(builder.build());
        return rawToken;
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
