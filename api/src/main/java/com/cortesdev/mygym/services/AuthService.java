package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.GoogleLoginRequest;
import com.cortesdev.mygym.models.dto.LoginResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.security.GoogleTokenVerifier;
import com.cortesdev.mygym.security.JwtService;
import com.cortesdev.mygym.services.exception.DemoAccessExpiredException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.UnauthorizedGoogleLoginException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final GymRepository gymRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtService jwtService;
    private final MemberLifecycleEmailService memberLifecycleEmailService;
    private final DemoAccessService demoAccessService;

    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(request.idToken());
        AppUser user = appUserRepository
                .findByEmail(AppUser.normalizeEmail(identity.email()))
                .orElseThrow(() -> new UnauthorizedGoogleLoginException(identity.email()));
        // Chequear ANTES que el guard genérico de abajo: un acceso demo vencido tiene su propia
        // excepción (410, ver DemoAccessExpiredException) para que el frontend mande al
        // formulario de contacto en vez de mostrar "cuenta no registrada" a secas.
        if (demoAccessService.expireIfNeeded(user)) {
            throw new DemoAccessExpiredException();
        }
        if (!user.isActive()) {
            throw new UnauthorizedGoogleLoginException(identity.email());
        }
        updateGoogleProfile(user, identity);
        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getRole(), user.getGymId());
        return new LoginResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getGymId(),
                false,
                user.getPhotoUrl());
    }

    // lastLoginAt se setea SIEMPRE (por eso ya no hace falta el flag `changed`: con eso solo,
    // este método ya guarda en cada login) — a diferencia de googleSub (inmutable, solo la
    // primera vez) y photoUrl (solo si Google mandó una distinta a la guardada).
    private void updateGoogleProfile(AppUser user, GoogleTokenVerifier.GoogleIdentity identity) {
        if (user.getGoogleSub() == null) {
            user.setGoogleSub(identity.googleSub());
        }
        if (!Objects.equals(user.getPhotoUrl(), identity.pictureUrl())) {
            user.setPhotoUrl(identity.pictureUrl());
        }
        user.setLastLoginAt(Instant.now());
        appUserRepository.save(user);
    }

    /**
     * Public self-signup for a specific gym's join page (mygym.cl/j/{slug}).
     * Deliberately separate from loginWithGoogle: the gymId always comes from
     * the slug resolved here on the server, never from client input, so this
     * can only ever create a MEMBER of one specific gym — it cannot be used
     * to create or escalate an admin account. If the email already exists
     * (any role), this just logs them in as-is; it never changes an existing
     * user's role or gym.
     */
    public LoginResponse joinGymWithGoogle(String idToken, String gymSlug) {
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(idToken);
        Gym gym = gymRepository.findBySlug(gymSlug).orElseThrow(() -> new GymNotFoundException(gymSlug));
        if (!gym.isActive()) {
            throw new GymNotFoundException(gymSlug);
        }

        var existingUser = appUserRepository.findByEmail(AppUser.normalizeEmail(identity.email()));
        boolean isNewMember = existingUser.isEmpty();
        AppUser user = existingUser.orElseGet(() -> appUserRepository.save(
                AppUser.builder()
                        .email(identity.email())
                        .name(identity.name())
                        .role(Role.MEMBER)
                        .gymId(gym.getId())
                        .active(true)
                        .googleSub(identity.googleSub())
                        .photoUrl(identity.pictureUrl())
                        .build()));

        if (isNewMember) {
            memberLifecycleEmailService.sendMemberWelcome(gym, user);
            List<String> adminEmails = appUserRepository.findByGymIdAndRole(gym.getId(), Role.GYM_ADMIN).stream()
                    .map(AppUser::getEmail)
                    .toList();
            memberLifecycleEmailService.sendNewMemberNotice(gym, user, adminEmails);
        }

        if (!user.isActive()) {
            throw new UnauthorizedGoogleLoginException(identity.email());
        }
        updateGoogleProfile(user, identity);
        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getRole(), user.getGymId());
        return new LoginResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getGymId(),
                isNewMember,
                user.getPhotoUrl());
    }
}
