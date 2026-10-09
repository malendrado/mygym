package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.OccurrenceSearchResult;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unidades puras (Mockito, sin DB): mínimo de letras y tope de socios del buscador de reservas futuras. */
@ExtendWith(MockitoExtension.class)
class ReservationServiceSearchTest {

    private static final Long GYM_ID = 3L;

    @Mock private AppUserRepository appUserRepository;
    @Mock private ReservationRepository reservationRepository;
    @InjectMocks private ReservationService reservationService;

    private List<AppUser> members(int count) {
        List<AppUser> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(AppUser.builder().id((long) i).name("Ana " + String.format("%02d", i)).role(Role.MEMBER).build());
        }
        return list;
    }

    @Test
    void shortOrBlankQueryNeverTouchesTheDatabase() {
        for (String q : new String[] {null, "", "   ", "a", " a "}) {
            OccurrenceSearchResult result = reservationService.searchUpcomingReservations(GYM_ID, q);
            assertThat(result.occurrences()).isEmpty();
            assertThat(result.truncated()).isFalse();
        }
        verify(appUserRepository, never()).findByGymIdAndRoleAndNameContainingIgnoreCase(any(), any(), any());
    }

    @Test
    void moreMatchesThanTheCapAreTruncatedToTheFirstAlphabetically() {
        // llegan desordenados: el servicio los ordena por nombre antes de aplicar el tope
        List<AppUser> matches = members(40);
        java.util.Collections.reverse(matches);
        when(appUserRepository.findByGymIdAndRoleAndNameContainingIgnoreCase(GYM_ID, Role.MEMBER, "ana"))
                .thenReturn(matches);
        when(reservationRepository.findByMemberIdInAndStatusAndClassDateGreaterThanEqual(
                        anyList(), eq(ReservationStatus.BOOKED), any(LocalDate.class)))
                .thenReturn(List.of());

        OccurrenceSearchResult result = reservationService.searchUpcomingReservations(GYM_ID, "  ana ");

        assertThat(result.truncated()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository)
                .findByMemberIdInAndStatusAndClassDateGreaterThanEqual(ids.capture(), any(), any());
        assertThat(ids.getValue()).hasSize(ReservationService.MAX_SEARCH_MEMBERS);
        assertThat(ids.getValue().get(0)).isEqualTo(1L); // "Ana 01" primero
        assertThat(ids.getValue().get(24)).isEqualTo(25L);
    }

    @Test
    void matchesWithinTheCapAreNotTruncated() {
        when(appUserRepository.findByGymIdAndRoleAndNameContainingIgnoreCase(GYM_ID, Role.MEMBER, "ana"))
                .thenReturn(members(ReservationService.MAX_SEARCH_MEMBERS));
        when(reservationRepository.findByMemberIdInAndStatusAndClassDateGreaterThanEqual(
                        anyList(), eq(ReservationStatus.BOOKED), any(LocalDate.class)))
                .thenReturn(List.of());

        assertThat(reservationService.searchUpcomingReservations(GYM_ID, "ana").truncated()).isFalse();
    }
}
