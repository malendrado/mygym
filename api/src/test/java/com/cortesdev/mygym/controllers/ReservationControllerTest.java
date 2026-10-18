package com.cortesdev.mygym.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.ClassWaitlistEntry;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.ClassWaitlistRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Autowired
    private ClassWaitlistRepository waitlistRepository;

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
                .andExpect(jsonPath("$.title").value("Cupo excedido"));
    }

    @Test
    void book_lessThanTwoHoursBefore_returns400() throws Exception {
        Gym gym = createGym();
        // La clase empieza en 30 min: pasadas las 23:30 eso cae mañana, así que la fecha sale del instante sumado
        // (antes se usaba siempre "hoy" y el test fallaba cada noche después de las 22:30).
        LocalDateTime startsAt = LocalDateTime.now(ZONE).plusMinutes(30);
        LocalDate today = startsAt.toLocalDate();
        GymBlock block = createBlock(gym.getId(), today.getDayOfWeek(), startsAt.toLocalTime(), endAfter(startsAt), 5);
        AppUser member = createMember(gym.getId());
        String token = jwtService.issueToken(member.getId(), member.getEmail(), Role.MEMBER, gym.getId());
        String body = objectMapper.writeValueAsString(new ReservationPayload(block.getId(), today.toString()));

        mockMvc.perform(post("/api/me/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ventana de reserva cerrada"));
    }

    @Test
    void cancel_lessThanTwoHoursBefore_returns400() throws Exception {
        Gym gym = createGym();
        // Misma razón que en book_lessThanTwoHoursBefore: la fecha sale de "ahora + 30 min", no de "hoy".
        LocalDateTime startsAt = LocalDateTime.now(ZONE).plusMinutes(30);
        LocalDate today = startsAt.toLocalDate();
        GymBlock block = createBlock(gym.getId(), today.getDayOfWeek(), startsAt.toLocalTime(), endAfter(startsAt), 5);
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
                .andExpect(jsonPath("$.title").value("Ventana de reserva cerrada"));
    }

    @Test
    void myWaitlist_returnsOnlyMyFutureEntriesWithPlaceInLine() throws Exception {
        Gym gym = createGym();
        LocalDate classDate = LocalDate.now(ZONE).plusDays(2);
        GymBlock block = createBlock(gym.getId(), classDate.getDayOfWeek(), LocalTime.of(19, 0), LocalTime.of(20, 0), 1);
        AppUser first = createMember(gym.getId());
        AppUser me = createMember(gym.getId());
        AppUser third = createMember(gym.getId());
        java.time.Instant t0 = java.time.Instant.now().minusSeconds(600);
        waitlistRepository.save(entry(block.getId(), first.getId(), classDate, t0));
        waitlistRepository.save(entry(block.getId(), me.getId(), classDate, t0.plusSeconds(60)));
        waitlistRepository.save(entry(block.getId(), third.getId(), classDate, t0.plusSeconds(120)));
        // otra clase, donde soy el único; y una espera ya vencida (ayer) que no debe aparecer
        GymBlock other = createBlock(gym.getId(), classDate.getDayOfWeek(), LocalTime.of(7, 0), LocalTime.of(8, 0), 1);
        waitlistRepository.save(entry(other.getId(), me.getId(), classDate, t0));
        waitlistRepository.save(entry(block.getId(), me.getId(), LocalDate.now(ZONE).minusDays(1), t0));
        // aislamiento entre gyms: la espera de un socio de otro gym no se mezcla
        Gym otherGym = createGym();
        GymBlock foreignBlock = createBlock(otherGym.getId(), classDate.getDayOfWeek(), LocalTime.NOON, LocalTime.NOON.plusHours(1), 1);
        AppUser foreigner = createMember(otherGym.getId());
        waitlistRepository.save(entry(foreignBlock.getId(), foreigner.getId(), classDate, t0));
        String token = jwtService.issueToken(me.getId(), me.getEmail(), Role.MEMBER, gym.getId());

        mockMvc.perform(get("/api/me/waitlist").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].gymBlockId").value(other.getId()))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].startTime").value("07:00:00"))
                .andExpect(jsonPath("$[1].gymBlockId").value(block.getId()))
                .andExpect(jsonPath("$[1].classDate").value(classDate.toString()))
                .andExpect(jsonPath("$[1].label").value("Test Block"))
                .andExpect(jsonPath("$[1].endTime").value("20:00:00"))
                .andExpect(jsonPath("$[1].position").value(2));

        String foreignToken = jwtService.issueToken(foreigner.getId(), foreigner.getEmail(), Role.MEMBER, otherGym.getId());
        mockMvc.perform(get("/api/me/waitlist").header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].position").value(1));
    }

    private static ClassWaitlistEntry entry(Long blockId, Long memberId, LocalDate date, java.time.Instant createdAt) {
        return ClassWaitlistEntry.builder().gymBlockId(blockId).memberId(memberId).classDate(date).createdAt(createdAt).build();
    }

    private Gym createGym() {
        return gymRepository.save(Gym.builder()
                .name("Reservation Gym")
                .slug("reservation-gym-" + System.nanoTime())
                .maxUsers(50)
                .active(true)
                .googleLoginEnabled(true)
                // Las ventanas son por gimnasio y en minutos: los casos "menos de 2 horas" necesitan 120 explícitos.
                .bookingWindowMinutes(120)
                .cancellationWindowMinutes(120)
                .build());
    }

    /** Fin de la clase una hora después del inicio, sin pasar de medianoche (el bloque es de un solo día). */
    private static LocalTime endAfter(LocalDateTime start) {
        LocalDateTime end = start.plusHours(1);
        return end.toLocalDate().equals(start.toLocalDate()) ? end.toLocalTime() : LocalTime.of(23, 59);
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

    // Reservar exige plan pagado vigente y datos de contacto completos (ReservationService.book): sin ellos
    // responde 402/422 antes de llegar a lo que estas pruebas quieren verificar.
    private AppUser createMember(Long gymId) {
        return appUserRepository.save(AppUser.builder()
                .email("member-" + System.nanoTime() + "@test.com")
                .name("Test Member")
                .role(Role.MEMBER)
                .gymId(gymId)
                .active(true)
                .paidAt(java.time.Instant.now())
                .rut("12.345.678-5")
                .address("Av. Siempre Viva 742, Ñuñoa")
                .phone("+56912345678")
                .birthDate(LocalDate.of(1990, 5, 20))
                .emergencyContactName("Marta Pérez")
                .emergencyContactPhone("+56987654321")
                .build());
    }

    private record ReservationPayload(Long gymBlockId, String classDate) {}
}
