package com.cortesdev.mygym.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ReservationControllerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GymRepository gymRepository;

    @Autowired
    private GymBlockRepository gymBlockRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void book_thenCancel_happyPath() throws Exception {
        Gym gym = createGym();
        LocalDate classDate = LocalDate.now(ZONE).plusDays(1);
        GymBlock block =
                createBlock(gym.getId(), classDate.getDayOfWeek(), LocalTime.NOON, LocalTime.NOON.plusHours(1), 5);
        AppUser member = createMember(gym.getId());
        String token = jwtService.issueToken(member.getId(), member.getEmail(), Role.MEMBER, gym.getId());

        String body = objectMapper.writeValueAsString(new ReservationPayload(block.getId(), classDate.toString()));
        String response = mockMvc.perform(post("/api/me/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long reservationId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/me/reservations/" + reservationId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void book_capacityFull_returns409() throws Exception {
        Gym gym = createGym();
        LocalDate classDate = LocalDate.now(ZONE).plusDays(1);
        GymBlock block =
                createBlock(gym.getId(), classDate.getDayOfWeek(), LocalTime.NOON, LocalTime.NOON.plusHours(1), 1);
        AppUser member1 = createMember(gym.getId());
        AppUser member2 = createMember(gym.getId());
        String token1 = jwtService.issueToken(member1.getId(), member1.getEmail(), Role.MEMBER, gym.getId());
        String token2 = jwtService.issueToken(member2.getId(), member2.getEmail(), Role.MEMBER, gym.getId());
        String body = objectMapper.writeValueAsString(new ReservationPayload(block.getId(), classDate.toString()));

        mockMvc.perform(post("/api/me/reservations")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/me/reservations")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Capacity exceeded"));
    }

    @Test
    void book_lessThanTwoHoursBefore_returns400() throws Exception {
        Gym gym = createGym();
        LocalDate today = LocalDate.now(ZONE);
        DayOfWeek todayDow = today.getDayOfWeek();
        GymBlock block = createBlock(
                gym.getId(), todayDow, LocalTime.now(ZONE).plusMinutes(30), LocalTime.now(ZONE).plusMinutes(90), 5);
        AppUser member = createMember(gym.getId());
        String token = jwtService.issueToken(member.getId(), member.getEmail(), Role.MEMBER, gym.getId());
        String body = objectMapper.writeValueAsString(new ReservationPayload(block.getId(), today.toString()));

        mockMvc.perform(post("/api/me/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Booking window closed"));
    }

    @Test
    void cancel_lessThanTwoHoursBefore_returns400() throws Exception {
        Gym gym = createGym();
        LocalDate today = LocalDate.now(ZONE);
        GymBlock block = createBlock(
                gym.getId(),
                today.getDayOfWeek(),
                LocalTime.now(ZONE).plusMinutes(30),
                LocalTime.now(ZONE).plusMinutes(90),
                5);
        AppUser member = createMember(gym.getId());
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .gymBlockId(block.getId())
                .memberId(member.getId())
                .classDate(today)
                .status(ReservationStatus.BOOKED)
                .build());
        String token = jwtService.issueToken(member.getId(), member.getEmail(), Role.MEMBER, gym.getId());

        mockMvc.perform(
                        delete("/api/me/reservations/" + reservation.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Booking window closed"));
    }

    private Gym createGym() {
        return gymRepository.save(Gym.builder()
                .name("Reservation Gym")
                .slug("reservation-gym-" + System.nanoTime())
                .maxUsers(50)
                .active(true)
                .googleLoginEnabled(true)
                .build());
    }

    private GymBlock createBlock(Long gymId, DayOfWeek dayOfWeek, LocalTime start, LocalTime end, int capacity) {
        return gymBlockRepository.save(GymBlock.builder()
                .gymId(gymId)
                .label("Test Block")
                .dayOfWeek(dayOfWeek)
                .startTime(start)
                .endTime(end)
                .capacity(capacity)
                .active(true)
                .build());
    }

    private AppUser createMember(Long gymId) {
        return appUserRepository.save(AppUser.builder()
                .email("member-" + System.nanoTime() + "@test.com")
                .name("Test Member")
                .role(Role.MEMBER)
                .gymId(gymId)
                .active(true)
                .build());
    }

    private record ReservationPayload(Long gymBlockId, String classDate) {}
}
