package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.models.dto.MemberSummaryResponse;
import com.cortesdev.mygym.models.dto.PageResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unidades puras (Mockito, sin DB): búsqueda paginada, filtros y conteos de la pestaña Socios. */
@ExtendWith(MockitoExtension.class)
class MemberServiceSearchTest {

    private static final Long GYM_ID = 4L;
    private static final ZoneId ZONE = ZoneId.of("America/Santiago");

    @Mock private AppUserRepository appUserRepository;
    @Mock private com.cortesdev.mygym.repositories.MemberFixedClassRepository memberFixedClassRepository;
    @InjectMocks private MemberService memberService;

    private long nextId = 1;

    private AppUser member(String name, String email, Instant paidAt, Instant invitedAt, String googleSub) {
        return AppUser.builder()
                .id(nextId++)
                .name(name)
                .email(email)
                .role(Role.MEMBER)
                .gymId(GYM_ID)
                .active(true)
                .paidAt(paidAt)
                .invitedAt(invitedAt)
                .googleSub(googleSub)
                .build();
    }

    private Instant daysAgo(int days) {
        return ZonedDateTime.now(ZONE).minusDays(days).toInstant();
    }

    @BeforeEach
    void seed() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        Instant expiringSoon = now.minusMonths(1).plusDays(2).toInstant();
        List<AppUser> roster = new ArrayList<>(List.of(
                member("zoe", "zoe@x.cl", daysAgo(5), null, null), // ACTIVE
                member("Ana", "ana@x.cl", expiringSoon, null, null), // EXPIRING_SOON
                member("beto", "beto@x.cl", daysAgo(90), null, null), // EXPIRED
                member("Carla", "carla@x.cl", null, null, null), // UNPAID
                member("diego", "diego@x.cl", null, Instant.now(), null), // UNPAID + invitado pendiente
                member("Elena", "elena@x.cl", daysAgo(3), Instant.now(), "sub-1"))); // ACTIVE + invitada registrada
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.MEMBER)).thenReturn(roster);
    }

    @Test
    void ordersByNameIgnoringCaseAndPaginates() {
        PageResponse<MemberResponse> first = memberService.searchMembers(GYM_ID, null, null, null, 0, 4);
        assertThat(first.items()).extracting(MemberResponse::name).containsExactly("Ana", "beto", "Carla", "diego");
        assertThat(first.totalElements()).isEqualTo(6);
        assertThat(first.hasNext()).isTrue();

        PageResponse<MemberResponse> second = memberService.searchMembers(GYM_ID, null, null, null, 1, 4);
        assertThat(second.items()).extracting(MemberResponse::name).containsExactly("Elena", "zoe");
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void findsMembersByRutOrPhoneIgnoringFormatButOnlyWithThreeOrMoreDigits() {
        List<AppUser> roster = appUserRepository.findByGymIdAndRole(GYM_ID, Role.MEMBER);
        AppUser zoe = roster.stream().filter(u -> "zoe".equals(u.getName())).findFirst().orElseThrow();
        zoe.setRut("12.345.678-5");
        zoe.setPhone("+56912345678");

        for (String needle : new String[] {"12345678", "12.345.678-5", "9 1234 5678", "+56 9 1234 5678"}) {
            assertThat(memberService.searchMembers(GYM_ID, needle, null, null, 0, 10).items())
                    .as(needle)
                    .extracting(MemberResponse::name)
                    .containsExactly("zoe");
        }
        assertThat(memberService.searchMembers(GYM_ID, "12", null, null, 0, 10).items()).isEmpty();
    }

    @Test
    void pageBeyondTheEndIsEmptyAndSizeIsClamped() {
        PageResponse<MemberResponse> beyond = memberService.searchMembers(GYM_ID, null, null, null, 99, 10);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.hasNext()).isFalse();
        assertThat(beyond.totalElements()).isEqualTo(6);

        PageResponse<MemberResponse> huge = memberService.searchMembers(GYM_ID, null, null, null, -5, 100_000);
        assertThat(huge.page()).isZero();
        assertThat(huge.size()).isEqualTo(50);
        assertThat(huge.items()).hasSize(6);
    }

    @Test
    void filtersByStatusInviteAndTextTogether() {
        assertThat(memberService.searchMembers(GYM_ID, null, "ACTIVE", null, 0, 25).items())
                .extracting(MemberResponse::name)
                .containsExactly("Elena", "zoe");
        assertThat(memberService.searchMembers(GYM_ID, null, "UNPAID", null, 0, 25).items())
                .extracting(MemberResponse::name)
                .containsExactly("Carla", "diego");
        assertThat(memberService.searchMembers(GYM_ID, null, null, "PENDING", 0, 25).items())
                .extracting(MemberResponse::name)
                .containsExactly("diego");
        // texto: por nombre o por email, sin importar mayúsculas — y combinado con estado (AND)
        assertThat(memberService.searchMembers(GYM_ID, "  ELE ", null, null, 0, 25).items())
                .extracting(MemberResponse::name)
                .containsExactly("Elena");
        assertThat(memberService.searchMembers(GYM_ID, "x.cl", "EXPIRED", null, 0, 25).items())
                .extracting(MemberResponse::name)
                .containsExactly("beto");
        assertThat(memberService.searchMembers(GYM_ID, "nadie", null, null, 0, 25).items()).isEmpty();
    }

    @Test
    void expiredAndExpiringSoonOrderByDueDateWithStablePagination() {
        List<AppUser> roster = appUserRepository.findByGymIdAndRole(GYM_ID, Role.MEMBER);
        Instant sameDay = daysAgo(60);
        roster.add(member("Yara", "yara@x.cl", sameDay, null, null)); // EXPIRED, empata fecha con Mario
        roster.add(member("Mario", "mario@x.cl", sameDay, null, null)); // EXPIRED
        roster.add(member("Olga", "olga@x.cl", daysAgo(200), null, null)); // EXPIRED, la más antigua
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        roster.add(member("Zara", "zara@x.cl", now.minusMonths(1).plusDays(1).toInstant(), null, null)); // vence antes que Ana

        // más antiguo primero, empate por fecha resuelto por nombre; sin duplicados ni saltos entre páginas
        PageResponse<MemberResponse> p0 = memberService.searchMembers(GYM_ID, null, "EXPIRED", null, 0, 2);
        PageResponse<MemberResponse> p1 = memberService.searchMembers(GYM_ID, null, "EXPIRED", null, 1, 2);
        PageResponse<MemberResponse> p2 = memberService.searchMembers(GYM_ID, null, "EXPIRED", null, 2, 2);
        assertThat(p0.items()).extracting(MemberResponse::name).containsExactly("Olga", "beto");
        assertThat(p1.items()).extracting(MemberResponse::name).containsExactly("Mario", "Yara");
        assertThat(p2.items()).isEmpty();
        assertThat(p0.hasNext()).isTrue();
        assertThat(p1.hasNext()).isFalse();

        assertThat(memberService.searchMembers(GYM_ID, null, "EXPIRING_SOON", null, 0, 10).items())
                .extracting(MemberResponse::name)
                .containsExactly("Zara", "Ana");

        // sin filtro, ACTIVE y UNPAID siguen alfabéticos
        assertThat(memberService.searchMembers(GYM_ID, null, null, null, 0, 50).items())
                .extracting(MemberResponse::name)
                .containsExactly("Ana", "beto", "Carla", "diego", "Elena", "Mario", "Olga", "Yara", "Zara", "zoe");
        assertThat(memberService.searchMembers(GYM_ID, null, "UNPAID", null, 0, 50).items())
                .extracting(MemberResponse::name)
                .containsExactly("Carla", "diego");
    }

    @Test
    void summaryCountsTheWholeGymWithTheSameRules() {
        MemberSummaryResponse summary = memberService.memberSummary(GYM_ID);
        assertThat(summary.total()).isEqualTo(6);
        assertThat(summary.active()).isEqualTo(2);
        assertThat(summary.expiringSoon()).isEqualTo(1);
        assertThat(summary.expired()).isEqualTo(1);
        assertThat(summary.unpaid()).isEqualTo(2);
        assertThat(summary.invitedPending()).isEqualTo(1);
    }
}
