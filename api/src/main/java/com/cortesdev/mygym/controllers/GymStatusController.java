package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado del gimnasio del usuario con sesion (socio, admin o profesor). Es la UNICA ruta que el filtro de pausa deja pasar
 * cuando el gimnasio esta en pausa: la app la consulta para mostrar el aviso con los datos de contacto correctos. Cuando el
 * gimnasio esta activo no devuelve ningun dato de contacto.
 */
@RestController
@RequestMapping("/api/gym-status")
@RequiredArgsConstructor
public class GymStatusController {

    public record Contact(String whatsappNumber, String email, String instagramUrl) {}

    /** `gymContact` es lo que ve un socio (como contactar a su gimnasio); `mygymContact` lo que ve el equipo del gimnasio. */
    public record GymStatusResponse(boolean paused, String gymName, Contact gymContact, Contact mygymContact) {}

    private final GymRepository gymRepository;
    private final AppUserRepository appUserRepository;

    @Value("${app.contact.whatsapp:56964641042}")
    private String mygymWhatsapp;

    @Value("${app.contact.email:contacto@mygym.cl}")
    private String mygymEmail;

    @GetMapping
    public GymStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        if (gym.isActive()) {
            return new GymStatusResponse(false, gym.getName(), null, null);
        }
        // Correo de contacto del gimnasio: el del PRIMER administrador que se creo (el dueño).
        String ownerEmail = appUserRepository.findByGymIdAndRole(gymId, Role.GYM_ADMIN).stream()
                .min(Comparator.comparing(AppUser::getId))
                .map(AppUser::getEmail)
                .orElse(null);
        return new GymStatusResponse(
                true,
                gym.getName(),
                new Contact(gym.getWhatsappNumber(), ownerEmail, gym.getInstagramUrl()),
                new Contact(mygymWhatsapp, mygymEmail, null));
    }
}
