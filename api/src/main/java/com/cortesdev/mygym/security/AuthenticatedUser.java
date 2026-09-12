package com.cortesdev.mygym.security;

import com.cortesdev.mygym.models.Role;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthenticatedUser(Long userId, String email, Role role, Long gymId) {

    public static AuthenticatedUser from(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        Number gymId = jwt.getClaim("gymId");
        return new AuthenticatedUser(
                userId.longValue(),
                jwt.getSubject(),
                Role.valueOf(jwt.getClaimAsString("role")),
                gymId != null ? gymId.longValue() : null);
    }
}
