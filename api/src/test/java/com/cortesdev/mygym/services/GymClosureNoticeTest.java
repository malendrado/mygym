package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.dto.GymClosureNoticeResponse;
import com.cortesdev.mygym.repositories.GymClosureRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Aviso de cierre de /member (Mockito, sin DB): que un cierre levantado deje de mostrarse. */
@ExtendWith(MockitoExtension.class)
class GymClosureNoticeTest {

    private static final Long GYM_ID = 9L;
    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final LocalDate TODAY = LocalDate.now(ZONE);

    @Mock private GymClosureRepository gymClosureRepository;
    @InjectMocks private GymClosureService gymClosureService;

    private GymClosure closure(int startOffset, int endOffset, Instant liftedAt) {
        return GymClosure.builder()
                .id(1L).gymId(GYM_ID).wholeDays(true).reason("Cierre del gym")
                .startDate(TODAY.plusDays(startOffset)).endDate(TODAY.plusDays(endOffset)).liftedAt(liftedAt).build();
    }

    private GymClosureNoticeResponse noticeFor(GymClosure... closures) {
        when(gymClosureRepository.findByGymIdAndEndDateGreaterThanEqual(eq(GYM_ID), any())).thenReturn(List.of(closures));
        return gymClosureService.activeOrUpcomingNotice(GYM_ID);
    }

    @Test
    void aClosureScheduledForTomorrowShowsAsUpcoming() {
        GymClosureNoticeResponse notice = noticeFor(closure(1, 1, null));

        assertThat(notice).isNotNull();
        assertThat(notice.startDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(notice.endDate()).isEqualTo(TODAY.plusDays(1));
    }

    /** El bug real: cierre del dia siguiente levantado hoy. Mostraba "del 16 al 15" y no desaparecia. */
    @Test
    void aClosureLiftedBeforeItStartedShowsNoNoticeAtAll() {
        GymClosureNoticeResponse notice = noticeFor(closure(1, 1, Instant.now()));

        assertThat(notice).isNull();
    }

    @Test
    void aClosureLiftedWhileActiveStopsShowingOnceItsCoveredDaysArePast() {
        // empezo ayer, termina en 3 dias, se levanta hoy: cubrio solo ayer -> ya no hay nada que avisar
        GymClosureNoticeResponse notice = noticeFor(closure(-1, 3, Instant.now()));

        assertThat(notice).isNull();
    }

    @Test
    void aLiftedClosureNeverHidesAnotherUpcomingOne() {
        GymClosureNoticeResponse notice = noticeFor(closure(1, 1, Instant.now()), closure(5, 6, null));

        assertThat(notice).isNotNull();
        assertThat(notice.startDate()).isEqualTo(TODAY.plusDays(5));
    }
}
