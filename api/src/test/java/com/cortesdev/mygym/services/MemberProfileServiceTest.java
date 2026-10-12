package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberProfileRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.services.exception.InvalidMemberProfileException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unidades puras (Mockito, sin DB): el socio o el admin guardan los cuatro datos juntos, o ninguno. */
@ExtendWith(MockitoExtension.class)
class MemberProfileServiceTest {

    private static final Long GYM_ID = 2L;
    private static final Long MEMBER_ID = 4L;

    @Mock private AppUserRepository appUserRepository;
    @Mock private MemberService memberService;
    @InjectMocks private MemberProfileService service;

    private final AppUser member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).build();

    private static MemberProfileRequest request(String rut, String phone) {
        return new MemberProfileRequest(rut, "  Av. Siempre Viva 742, Ñuñoa ", LocalDate.of(1990, 5, 20), phone);
    }

    @Test
    void ownProfileIsSavedNormalizedAndMakesTheMemberComplete() {
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        service.updateOwn(MEMBER_ID, request("123456785", "9 1234 5678"));

        assertThat(member.getRut()).isEqualTo("12.345.678-5");
        assertThat(member.getPhone()).isEqualTo("+56912345678");
        assertThat(member.getAddress()).isEqualTo("Av. Siempre Viva 742, Ñuñoa");
        assertThat(member.isProfileComplete()).isTrue();
        verify(appUserRepository).save(member);
    }

    @Test
    void anInvalidDataPointSavesNothingAtAll() {
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.updateOwn(MEMBER_ID, request("12.345.678-9", "9 1234 5678")))
                .isInstanceOf(InvalidMemberProfileException.class);

        assertThat(member.getRut()).isNull();
        assertThat(member.getPhone()).isNull();
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void anAdminCanOnlyEditMembersOfTheirOwnGym() {
        when(appUserRepository.findByIdAndGymId(99L, GYM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateForGym(GYM_ID, 99L, request("123456785", "912345678")))
                .isInstanceOf(MemberNotFoundException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void anAdminAccountIsNotAMemberProfile() {
        AppUser admin = AppUser.builder().id(8L).gymId(GYM_ID).role(Role.GYM_ADMIN).build();
        when(appUserRepository.findById(8L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateOwn(8L, request("123456785", "912345678")))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void profileIsIncompleteUntilAllFourDataPointsExist() {
        AppUser partial = AppUser.builder().rut("12.345.678-5").address("Av. X 123").phone("+56912345678").build();
        assertThat(partial.isProfileComplete()).isFalse();
        partial.setBirthDate(LocalDate.of(1990, 1, 1));
        assertThat(partial.isProfileComplete()).isTrue();
        partial.setPhone(" ");
        assertThat(partial.isProfileComplete()).isFalse();
    }
}
