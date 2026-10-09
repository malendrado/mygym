package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.GymClosureBlock;
import com.cortesdev.mygym.models.dto.GymClosureResponse;
import com.cortesdev.mygym.models.dto.PageResponse;
import com.cortesdev.mygym.repositories.GymClosureBlockRepository;
import com.cortesdev.mygym.repositories.GymClosureRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Unidades puras (Mockito, sin DB): historial de cierres paginado. */
@ExtendWith(MockitoExtension.class)
class GymClosureServiceListTest {

    private static final Long GYM_ID = 9L;

    @Mock private GymClosureRepository gymClosureRepository;
    @Mock private GymClosureBlockRepository gymClosureBlockRepository;
    @InjectMocks private GymClosureService gymClosureService;

    private GymClosure closure(long id, boolean wholeDays) {
        return GymClosure.builder()
                .id(id)
                .gymId(GYM_ID)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 1, 3))
                .wholeDays(wholeDays)
                .reason("Corte de luz")
                .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
    }

    @Test
    void pageIsClampedAndOrderedMostRecentFirst() {
        when(gymClosureRepository.findByGymId(eq(GYM_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(closure(1, true)), PageRequest.of(0, 50), 1));

        gymClosureService.listPage(GYM_ID, -2, 10_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(gymClosureRepository).findByGymId(eq(GYM_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void blockIdsOfAllPartialClosuresComeFromASingleQuery() {
        List<GymClosure> content = List.of(closure(1, true), closure(2, false), closure(3, false));
        when(gymClosureRepository.findByGymId(eq(GYM_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(content, PageRequest.of(0, 3), 7));
        when(gymClosureBlockRepository.findByClosureIdIn(anyList()))
                .thenReturn(List.of(
                        GymClosureBlock.builder().closureId(2L).gymBlockId(20L).build(),
                        GymClosureBlock.builder().closureId(2L).gymBlockId(21L).build(),
                        GymClosureBlock.builder().closureId(3L).gymBlockId(30L).build()));

        PageResponse<GymClosureResponse> page = gymClosureService.listPage(GYM_ID, 0, 3);

        assertThat(page.items()).extracting(GymClosureResponse::id).containsExactly(1L, 2L, 3L);
        assertThat(page.items().get(0).blockIds()).isEmpty(); // día completo: sin bloques puntuales
        assertThat(page.items().get(1).blockIds()).containsExactly(20L, 21L);
        assertThat(page.items().get(2).blockIds()).containsExactly(30L);
        assertThat(page.totalElements()).isEqualTo(7);
        assertThat(page.hasNext()).isTrue();
        // una sola consulta de bloques para toda la página, nunca una por cierre
        verify(gymClosureBlockRepository).findByClosureIdIn(List.of(2L, 3L));
        verify(gymClosureBlockRepository, never()).findByClosureId(any());
    }

    @Test
    void pageWithOnlyWholeDayClosuresSkipsTheBlocksQuery() {
        when(gymClosureRepository.findByGymId(eq(GYM_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(closure(1, true)), PageRequest.of(0, 10), 1));

        gymClosureService.listPage(GYM_ID, 0, 10);

        verify(gymClosureBlockRepository, never()).findByClosureIdIn(anyList());
    }
}
