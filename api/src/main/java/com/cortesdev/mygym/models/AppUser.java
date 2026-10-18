package com.cortesdev.mygym.models;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String googleSub;

    /** Hash bcrypt — null si esta cuenta nunca creó una contraseña (solo entra con Google). Convive
     *  con googleSub sin problema: el match sigue siendo por email, cualquiera de los dos métodos
     *  puede estar presente, ausente, o ambos a la vez. */
    private String passwordHash;

    /** Los JWT emitidos antes de este instante ya no valen (se fija al cambiar la contraseña). null = ninguna invalidación. */
    private Instant sessionsValidAfter;

    private String name;

    /** Foto de perfil de Google (claim "picture" del ID token) — null si nunca se logueó con Google. */
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Long gymId;

    private boolean active;

    /** Solo aplica a MEMBER — plan contratado y fecha del último pago (marcado a mano por un admin, ver GymService.simulatePlanPayment). */
    private Long planId;

    /** INSTANTE REAL del último pago (informes, correos, CSV). Los períodos NO salen de acá directo: ver MembershipPeriods. */
    private Instant paidAt;

    /** Ancla de la cadena de períodos pagados seguidos (renovación anticipada, V52). null = igual a paidAt. */
    private Instant periodStart;

    /** Meses calendario seguidos que cubre la cadena desde el ancla (V52). null = 1. */
    private Integer paidPeriods;

    /** Cambio de plan programado (V53): plan que pagó y se activará cuando se acaben las clases o llegue el vencimiento. */
    private Long pendingPlanId;

    /** Meses seguidos pagados del plan pendiente (null = 1). */
    private Integer pendingPlanPeriods;

    /** Solo aplica a MEMBER con plan de cupo limitado, cargado por el Excel de importación
     *  masiva (ver MemberImportRowService) — clases que ya venía usando en el sistema anterior
     *  del gym ese mismo mes. Se resta aparte al calcular sessionsRemaining (MemberService.
     *  toResponse) y se resetea a null apenas el plan se renueva o se le quita (GymService.
     *  simulatePlanPayment/revokePlan, FlowPaymentService) — nunca se arrastra a otro período. */
    private Integer usedSessionsAtImport;

    /** Seteado solo cuando un admin agrega este socio a mano (MemberService.createMember) — null si se
     *  auto-registró vía /j/{slug}. Junto con googleSub permite distinguir "invitado pendiente" (todavía
     *  no entró) de "invitado registrado" (ya entró al menos una vez), sin depender del email para nada
     *  más que su unicidad de siempre. */
    private Instant invitedAt;

    /** Datos de contacto obligatorios del socio (solo MEMBER). Null en los socios que ya existían:
     *  la app se los pide al entrar y no pueden reservar hasta completarlos (ver isProfileComplete). */
    private String rut;

    private String address;

    private LocalDate birthDate;

    /** Celular en formato internacional ("+56912345678") — sirve directo para wa.me. */
    private String phone;

    /** Contacto de emergencia (obligatorio, solo MEMBER): nombre y celular en formato internacional. */
    private String emergencyContactName;

    private String emergencyContactPhone;

    private Instant createdAt;

    private Instant updatedAt;

    /** Seteado en CADA login real con Google (AuthService.updateGoogleProfile) — a diferencia de
     *  updatedAt, que solo se toca cuando algo cambia (googleSub la primera vez, o si cambió la
     *  foto de Google), así que updatedAt no sirve para saber "cuándo entró por última vez". */
    private Instant lastLoginAt;

    /** Los datos de contacto y el contacto de emergencia cargados (los admins no los necesitan, ver ReservationService.book). */
    public boolean isProfileComplete() {
        return notBlank(rut) && notBlank(address) && notBlank(phone) && birthDate != null
                && notBlank(emergencyContactName) && notBlank(emergencyContactPhone);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        email = normalizeEmail(email);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        email = normalizeEmail(email);
    }

    /**
     * Bug real en prod: Google siempre devuelve el email en minúscula, pero un admin puede
     * tipear un email con mayúsculas al invitar — findByEmail/existsByEmail hacen match exacto,
     * así que "Nombre@mail.com" (invitado) y "nombre@mail.com" (login real de Google) generaban
     * DOS filas para la misma persona (el invitado quedaba pegado en PENDING para siempre, el
     * login real creaba una cuenta nueva sin el invitedAt). Normalizar acá garantiza que TODO lo
     * que se guarda queda en minúscula sin importar por dónde entre — pero los call sites de
     * findByEmail/existsByEmail igual tienen que normalizar el email de búsqueda (ver
     * AuthService/GymService/MemberService), esto solo cubre la escritura.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
