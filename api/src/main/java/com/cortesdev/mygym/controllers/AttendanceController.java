package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.AttendanceSummaryResponse;
import com.cortesdev.mygym.models.dto.AttendanceUpdateRequest;
import com.cortesdev.mygym.models.dto.MemberAttendanceDetail;
import com.cortesdev.mygym.models.dto.MemberAttendanceRow;
import com.cortesdev.mygym.models.dto.PageResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.AttendanceSummaryService;
import com.cortesdev.mygym.services.ReservationService;
import com.cortesdev.mygym.services.ReservationService.AttendeeMark;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Asistencia (marcado a mano y resúmenes) — lado GYM_ADMIN y PROFESOR (ver SecurityConfig; el GET
 *  también lo ve el DEMO_ADMIN). La contraparte SUPER_ADMIN vive en GymController (gymId por path).
 *  El gym SIEMPRE sale del JWT, nunca de un parámetro. */
@RestController
@RequestMapping("/api/gym-admin")
@RequiredArgsConstructor
public class AttendanceController {

    private final ReservationService reservationService;
    private final AttendanceSummaryService attendanceSummaryService;

    @PutMapping("/reservations/{reservationId}/attendance")
    public AttendeeMark setAttendance(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservationId,
            @Valid @RequestBody AttendanceUpdateRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return reservationService.setAttendance(user.gymId(), reservationId, request.attended(), user.userId());
    }

    @GetMapping("/attendance-summary")
    public AttendanceSummaryResponse summary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate previousFrom) {
        return attendanceSummaryService.summary(AuthenticatedUser.from(jwt).gymId(), from, to, previousFrom);
    }

    @GetMapping("/attendance-summary/members")
    public PageResponse<MemberAttendanceRow> members(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long blockId,
            @RequestParam(required = false) DayOfWeek weekday,
            @RequestParam(defaultValue = "false") boolean onlyMissed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return attendanceSummaryService.members(
                AuthenticatedUser.from(jwt).gymId(), from, to, blockId, weekday, onlyMissed, page, size);
    }

    @GetMapping("/attendance-summary/members/{memberId}")
    public MemberAttendanceDetail memberDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendanceSummaryService.memberDetail(AuthenticatedUser.from(jwt).gymId(), memberId, from, to);
    }
}
