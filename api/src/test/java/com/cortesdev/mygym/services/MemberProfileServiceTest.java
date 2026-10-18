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
        return new MemberProfileRequest(rut, "  Av. Siempre Viva 742, Ñuñoa ", LocalDate.of(1990, 5, 20), phone,
                " Marta  Pérez ", "9 8765 4321");
    }

    @Test
    void ownProfileIsSavedNormalizedAndMakesTheMemberComplete() {
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        service.updateOwn(MEMBER_ID, request("123456785", "9 1234 5678"));

        assertThat(member.getRut()).isEqualTo("12.345.678-5");
        assertThat(member.getPhone()).isEqualTo("+56912345678");
        assertThat(member.getAddress()).isEqualTo("Av. Siempre Viva 742, Ñuñoa");
        assertThat(member.getEmergencyContactName()).isEqualTo("Marta Pérez");
        assertThat(member.getEmergencyContactPhone()).isEqualTo("+56987654321");
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
    void profileIsIncompleteUntilAllSixDataPointsExist() {
        AppUser partial = AppUser.builder().rut("12.345.678-5").address("Av. X 123").phone("+56912345678").build();
        assertThat(partial.isProfileComplete()).isFalse();
        partial.setBirthDate(LocalDate.of(1990, 1, 1));
        // un socio que ya tenía sus datos de antes igual queda pendiente hasta dar su contacto de emergencia
        assertThat(partial.isProfileComplete()).isFalse();
        partial.setEmergencyContactName("Marta Pérez");
        assertThat(partial.isProfileComplete()).isFalse();
        partial.setEmergencyContactPhone("+56987654321");
        assertThat(partial.isProfileComplete()).isTrue();
        partial.setPhone(" ");
        assertThat(partial.isProfileComplete()).isFalse();
    }

    private AppUser completeMember() {
        return AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER)
                .rut("12.345.678-5").address("Av. Siempre Viva 742, Ñuñoa").birthDate(LocalDate.of(1990, 5, 20))
                .phone("+56912345678").emergencyContactName("Marta Pérez").emergencyContactPhone("+56987654321").build();
    }

    @Test
    void completeProfileAcceptsAPartialUpdateAndKeepsTheRest() {
        AppUser complete = completeMember();
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(complete));

        service.updateOwn(MEMBER_ID, new MemberProfileRequest(null, " ", null, "9 5555 4444", "", null));

        assertThat(complete.getPhone()).isEqualTo("+56955554444");
        assertThat(complete.getRut()).isEqualTo("12.345.678-5");
        assertThat(complete.getAddress()).isEqualTo("Av. Siempre Viva 742, Ñuñoa");
        assertThat(complete.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(complete.getEmergencyContactName()).isEqualTo("Marta Pérez");
        assertThat(complete.getEmergencyContactPhone()).isEqualTo("+56987654321");
        verify(appUserRepository).save(complete);
    }

    @Test
    void completeProfileWithNothingSentIsANoOpThatSucceeds() {
        AppUser complete = completeMember();
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(complete));

        service.updateOwn(MEMBER_ID, new MemberProfileRequest(null, null, null, null, null, null));

        assertThat(complete.getRut()).isEqualTo("12.345.678-5");
        assertThat(complete.getPhone()).isEqualTo("+56912345678");
        assertThat(complete.isProfileComplete()).isTrue();
        verify(appUserRepository).save(complete);
    }

    @Test
    void completeProfileStillValidatesTheFormatOfWhatIsSent() {
        AppUser complete = completeMember();
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(complete));

        assertThatThrownBy(() -> service.updateOwn(MEMBER_ID, new MemberProfileRequest("12.345.678-9", null, null, null, null, null)))
                .isInstanceOf(InvalidMemberProfileException.class).hasMessageStartingWith("RUT inválido");
        assertThatThrownBy(() -> service.updateOwn(MEMBER_ID, new MemberProfileRequest(null, null, null, "123", null, null)))
                .isInstanceOf(InvalidMemberProfileException.class);
        assertThatThrownBy(() -> service.updateOwn(MEMBER_ID, new MemberProfileRequest(null, null, LocalDate.now().plusDays(1), null, null, null)))
                .isInstanceOf(InvalidMemberProfileException.class);
        // y si uno es inválido no se guarda ni el válido que venía con él
        assertThatThrownBy(() -> service.updateOwn(MEMBER_ID, new MemberProfileRequest(null, "Otra dirección 99", null, "123", null, null)))
                .isInstanceOf(InvalidMemberProfileException.class);

        assertThat(complete.getAddress()).isEqualTo("Av. Siempre Viva 742, Ñuñoa");
        assertThat(complete.getPhone()).isEqualTo("+56912345678");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void incompleteProfileStillRequiresEverything() {
        AppUser incomplete = completeMember();
        incomplete.setEmergencyContactPhone(null); // un socio antiguo sin contacto de emergencia
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(incomplete));

        assertThatThrownBy(() -> service.updateOwn(MEMBER_ID, new MemberProfileRequest(null, null, null, "9 5555 4444", null, null)))
                .isInstanceOf(InvalidMemberProfileException.class);
        assertThatThrownBy(() -> service.updateOwn(MEMBER_ID, new MemberProfileRequest(
                "123456785", "Av. Siempre Viva 742", null, "9 5555 4444", "Marta", "9 8765 4321")))
                .isInstanceOf(InvalidMemberProfileException.class).hasMessage("Ingresa tu fecha de nacimiento.");

        assertThat(incomplete.getPhone()).isEqualTo("+56912345678");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void anInvalidEmergencyContactSavesNothingAtAll() {
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        MemberProfileRequest bad = new MemberProfileRequest(
                "123456785", "Av. Siempre Viva 742, Ñuñoa", LocalDate.of(1990, 5, 20), "9 1234 5678", "Marta", "22 123 4567");

        assertThatThrownBy(() -> service.updateOwn(MEMBER_ID, bad)).isInstanceOf(InvalidMemberProfileException.class);

        assertThat(member.getRut()).isNull();
        verify(appUserRepository, never()).save(any());
    }
}
