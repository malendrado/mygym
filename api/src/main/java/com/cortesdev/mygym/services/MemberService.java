package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberCreateRequest;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.services.exception.DuplicateMemberEmailException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final AppUserRepository appUserRepository;

    public MemberResponse createMember(Long gymId, MemberCreateRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new DuplicateMemberEmailException(request.email());
        }
        AppUser member = AppUser.builder()
                .name(request.name())
                .email(request.email())
                .role(Role.MEMBER)
                .gymId(gymId)
                .active(true)
                .build();
        return toResponse(appUserRepository.save(member));
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long gymId) {
        return appUserRepository.findByGymIdAndRole(gymId, Role.MEMBER).stream()
                .map(this::toResponse)
                .toList();
    }

    private MemberResponse toResponse(AppUser user) {
        return new MemberResponse(
                user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getGymId(), user.isActive(), user.getCreatedAt());
    }
}
