package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Dice dónde vive un email que ya tiene cuenta ("administrador del gimnasio «X»"), para mostrárselo SOLO al super admin
 * cuando un alta se rechaza por duplicado. A un administrador de gimnasio nunca se le revela: sería filtrar de qué gimnasio y
 * con qué rol es una cuenta ajena (ver el mensaje genérico de ApiExceptionHandler). Dos búsquedas por índice: email (único) e id.
 */
@Service
@RequiredArgsConstructor
public class EmailOwnerDescriber {

    private final AppUserRepository appUserRepository;
    private final GymRepository gymRepository;

    public Optional<String> describe(String email) {
        return appUserRepository.findByEmail(AppUser.normalizeEmail(email)).map(user -> {
            String role = roleLabel(user.getRole());
            if (user.getGymId() == null) {
                return role;
            }
            return gymRepository
                    .findById(user.getGymId())
                    .map(gym -> role + " del gimnasio «" + gym.getName() + "» (id " + gym.getId() + ")")
                    .orElse(role);
        });
    }

    private static String roleLabel(Role role) {
        return switch (role) {
            case SUPER_ADMIN -> "super administrador de mygym";
            case GYM_ADMIN -> "administrador";
            case PROFESOR -> "profesor";
            case MEMBER -> "socio";
            case DEMO_ADMIN -> "acceso de demo (solo lectura)";
        };
    }
}
