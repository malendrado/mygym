package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberProfileRequest;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Datos de contacto del socio (RUT, dirección, fecha de nacimiento, celular): los completa él
 *  mismo al entrar (pantalla obligatoria) o los carga/corrige el admin. Siempre los cuatro juntos. */
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
        // Se normaliza TODO antes de tocar el socio: si un dato es inválido no se guarda ninguno.
        String rut = MemberProfileRules.normalizeRut(request.rut());
        String address = MemberProfileRules.normalizeAddress(request.address());
        String phone = MemberProfileRules.normalizePhone(request.phone());
        var birthDate = MemberProfileRules.validateBirthDate(request.birthDate());
        member.setRut(rut);
        member.setAddress(address);
        member.setPhone(phone);
        member.setBirthDate(birthDate);
        appUserRepository.save(member);
        return memberService.getOwnMembership(member.getId());
    }
}
