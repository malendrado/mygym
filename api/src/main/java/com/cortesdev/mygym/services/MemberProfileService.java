package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberProfileRequest;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.services.exception.InvalidMemberProfileException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Datos de contacto del socio (RUT, dirección, fecha de nacimiento, celular) y contacto de emergencia (nombre y celular): los
 *  completa él mismo al entrar (pantalla obligatoria) o los carga/corrige el admin. Mientras el perfil esté incompleto, los
 *  seis juntos; una vez completo, se puede corregir uno solo (lo no enviado queda como estaba). */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberProfileService {

    private final AppUserRepository appUserRepository;
    private final MemberService memberService;

    /** El propio socio. Solo un MEMBER: los admins no tienen estos datos. */
    public MemberResponse updateOwn(Long userId, MemberProfileRequest request) {
        AppUser member = appUserRepository
                .findById(userId)
                .filter(u -> u.getRole() == Role.MEMBER)
                .orElseThrow(() -> new MemberNotFoundException(userId));
        return save(member, request);
    }

    /** El admin del gym (o el super-admin) sobre un socio de ESE gym — nunca de otro. */
    public MemberResponse updateForGym(Long gymId, Long memberId, MemberProfileRequest request) {
        AppUser member = appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .filter(u -> u.getRole() == Role.MEMBER)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        return save(member, request);
    }

    private MemberResponse save(AppUser member, MemberProfileRequest request) {
        // Perfil ya completo: un dato ausente/nulo/en blanco significa "no cambiar" y solo se valida lo enviado
        // (así "Mis datos" puede corregir un solo campo). Perfil incompleto: los seis siguen siendo obligatorios.
        boolean partialAllowed = member.isProfileComplete();
        // Se normaliza TODO antes de tocar el socio: si un dato es inválido no se guarda ninguno.
        String rut = isBlank(request.rut()) && partialAllowed ? null : MemberProfileRules.normalizeRut(request.rut());
        String address =
                isBlank(request.address()) && partialAllowed ? null : MemberProfileRules.normalizeAddress(request.address());
        String phone = isBlank(request.phone()) && partialAllowed ? null : MemberProfileRules.normalizePhone(request.phone());
        LocalDate birthDate;
        if (request.birthDate() == null) {
            if (!partialAllowed) {
                throw new InvalidMemberProfileException("Ingresa tu fecha de nacimiento.");
            }
            birthDate = null;
        } else {
            birthDate = MemberProfileRules.validateBirthDate(request.birthDate());
        }
        String emergencyName = isBlank(request.emergencyContactName()) && partialAllowed
                ? null
                : MemberProfileRules.normalizeEmergencyName(request.emergencyContactName());
        String emergencyPhone = isBlank(request.emergencyContactPhone()) && partialAllowed
                ? null
                : MemberProfileRules.normalizeEmergencyPhone(request.emergencyContactPhone());
        if (rut != null) {
            member.setRut(rut);
        }
        if (address != null) {
            member.setAddress(address);
        }
        if (phone != null) {
            member.setPhone(phone);
        }
        if (birthDate != null) {
            member.setBirthDate(birthDate);
        }
        if (emergencyName != null) {
            member.setEmergencyContactName(emergencyName);
        }
        if (emergencyPhone != null) {
            member.setEmergencyContactPhone(emergencyPhone);
        }
        appUserRepository.save(member);
        return memberService.getOwnMembership(member.getId());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
