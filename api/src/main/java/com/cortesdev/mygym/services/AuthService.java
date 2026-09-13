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
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.UnauthorizedGoogleLoginException;
import java.util.List;
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

    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(request.idToken());
        AppUser user = appUserRepository
                .findByEmail(identity.email())
                .orElseThrow(() -> new UnauthorizedGoogleLoginException(identity.email()));
        if (!user.isActive()) {
            throw new UnauthorizedGoogleLoginException(identity.email());
        }
        if (user.getGoogleSub() == null) {
            user.setGoogleSub(identity.googleSub());
            appUserRepository.save(user);
        }
        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getRole(), user.getGymId());
        return new LoginResponse(
                token, user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getGymId(), false);
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

        var existingUser = appUserRepository.findByEmail(identity.email());
        boolean isNewMember = existingUser.isEmpty();
        AppUser user = existingUser.orElseGet(() -> appUserRepository.save(
                AppUser.builder()
                        .email(identity.email())
                        .name(identity.name())
                        .role(Role.MEMBER)
                        .gymId(gym.getId())
                        .active(true)
                        .googleSub(identity.googleSub())
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
        if (user.getGoogleSub() == null) {
            user.setGoogleSub(identity.googleSub());
            appUserRepository.save(user);
        }
        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getRole(), user.getGymId());
        return new LoginResponse(
                token, user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getGymId(), isNewMember);
    }
}
