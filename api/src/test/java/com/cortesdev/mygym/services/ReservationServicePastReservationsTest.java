package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.dto.PageResponse;
import com.cortesdev.mygym.models.dto.ReservationResponse;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Unidades puras (Mockito, sin DB): paginación de "Mis reservas · Pasadas" y corte de "Próximas". */
@ExtendWith(MockitoExtension.class)
class ReservationServicePastReservationsTest {

    private static final Long MEMBER_ID = 7L;

    @Mock private ReservationRepository reservationRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @InjectMocks private ReservationService reservationService;

    private Reservation reservation(long id, LocalDate date) {
        return Reservation.builder()
                .id(id)
                .gymBlockId(3L)
                .memberId(MEMBER_ID)
                .classDate(date)
                .status(ReservationStatus.BOOKED)
                .build();
    }

    private GymBlock block() {
        return GymBlock.builder().id(3L).label("WOD").startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(8, 0)).build();
    }

    @Test
    void pastPageIsClampedAndOrderedMostRecentFirst() {
        Page<Reservation> result = new PageImpl<>(List.of(reservation(2, LocalDate.of(2026, 1, 2))), PageRequest.of(0, 50), 1);
        when(reservationRepository.findByMemberIdAndStatusAndClassDateLessThan(
                        eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(result);
        when(gymBlockRepository.findAllById(any())).thenReturn(List.of(block()));

        // size absurdo y page negativo — el servicio nunca los respeta tal cual
        reservationService.myPastReservations(MEMBER_ID, -3, 10_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationRepository)
                .findByMemberIdAndStatusAndClassDateLessThan(eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "classDate").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void pastPageReportsHasNextAndMapsBlockData() {
        List<Reservation> content = List.of(reservation(9, LocalDate.of(2026, 1, 9)), reservation(8, LocalDate.of(2026, 1, 8)));
        // total 5 con página de 2 => hay más páginas
        when(reservationRepository.findByMemberIdAndStatusAndClassDateLessThan(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(content, PageRequest.of(0, 2), 5));
        when(gymBlockRepository.findAllById(any())).thenReturn(List.of(block()));

        PageResponse<ReservationResponse> page = reservationService.myPastReservations(MEMBER_ID, 0, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).id()).isEqualTo(9L);
        assertThat(page.items().get(0).blockLabel()).isEqualTo("WOD");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    void lastPageHasNoNext() {
        when(reservationRepository.findByMemberIdAndStatusAndClassDateLessThan(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(reservation(1, LocalDate.of(2026, 1, 1))), PageRequest.of(2, 2), 5));
        when(gymBlockRepository.findAllById(any())).thenReturn(List.of(block()));

        assertThat(reservationService.myPastReservations(MEMBER_ID, 2, 2).hasNext()).isFalse();
    }

    @Test
    void upcomingUsesTodayAsLowerBound() {
        when(reservationRepository.findByMemberIdAndStatusAndClassDateGreaterThanEqualOrderByClassDateAsc(
                        eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any(LocalDate.class)))
                .thenReturn(List.of());

        assertThat(reservationService.myReservations(MEMBER_ID)).isEmpty();
        verify(reservationRepository)
                .findByMemberIdAndStatusAndClassDateGreaterThanEqualOrderByClassDateAsc(
                        eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any(LocalDate.class));
    }
}
