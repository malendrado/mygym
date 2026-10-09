package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.dto.GymStatsResponse;
import com.cortesdev.mygym.models.dto.GymSummaryResponse;
import com.cortesdev.mygym.models.dto.PageResponse;
import com.cortesdev.mygym.repositories.GymRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Unidades puras (Mockito, sin DB): lista paginada de gimnasios del super-admin y sus calugas. */
@ExtendWith(MockitoExtension.class)
class GymServiceListTest {

    @Mock private GymRepository gymRepository;
    @InjectMocks private GymService gymService;

    private GymSummaryResponse summary(long id, String name) {
        return new GymSummaryResponse(id, UUID.randomUUID(), name, name.toLowerCase(), true, 100, "#c6ff3d", null);
    }

    @Test
    void pageIsClampedAndPatternMatchesEverythingWhenThereIsNoQuery() {
        when(gymRepository.findSummariesPage(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1, "Alfa")), PageRequest.of(0, 50), 1));

        gymService.listGyms(null, "   ", -4, 10_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(gymRepository).findSummariesPage(eq(null), eq("%"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void mapsPageMetadata() {
        when(gymRepository.findSummariesPage(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1, "Alfa"), summary(2, "Beta")), PageRequest.of(0, 2), 5));

        PageResponse<GymSummaryResponse> page = gymService.listGyms(true, null, 0, 2);

        assertThat(page.items()).extracting(GymSummaryResponse::name).containsExactly("Alfa", "Beta");
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void searchTextIsLowercasedAndWildcardsAreEscaped() {
        assertThat(GymService.likePattern("  Gold GYM ")).isEqualTo("%gold gym%");
        assertThat(GymService.likePattern("100%_real\\x")).isEqualTo("%100\\%\\_real\\\\x%");
        assertThat(GymService.likePattern(null)).isEqualTo("%");
    }

    @Test
    void statsComeFromTheWholeTable() {
        when(gymRepository.count()).thenReturn(7L);
        when(gymRepository.countByActive(true)).thenReturn(5L);
        when(gymRepository.sumMaxUsers()).thenReturn(640L);
        when(gymRepository.countBranded()).thenReturn(3L);

        GymStatsResponse stats = gymService.gymStats();

        assertThat(stats).isEqualTo(new GymStatsResponse(7, 5, 640, 3));
    }
}
