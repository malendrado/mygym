package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Cuantas cuentas de un rol tiene un gimnasio (socios, profesores, admins): base de los limites de GymCapacityService. */
    long countByGymIdAndRole(Long gymId, Role role);

    List<AppUser> findByGymIdAndRole(Long gymId, Role role);

    /** Cualquier rol (admin, socio, demo) — usado por GymDisconnectionService para auditar y
     *  notificar antes de borrar; no reemplaza a findByGymIdAndRole, que sigue siendo más
     *  preciso para el resto de los usos. */
    List<AppUser> findByGymId(Long gymId);

    /** Buscador por nombre para el admin (cancelar la reserva de un socio puntual). */
    List<AppUser> findByGymIdAndRoleAndNameContainingIgnoreCase(Long gymId, Role role, String name);

    Optional<AppUser> findByIdAndGymId(Long id, Long gymId);

    /** Solo el gimnasio del socio, sin hidratar la entidad: permite leer el gym ANTES de bloquear la fila con findLockedByIdAndGymId
     *  (si la entidad ya estuviera en la sesión, el bloqueo devolvería su estado viejo en vez de releerla). */
    @org.springframework.data.jpa.repository.Query("select u.gymId from AppUser u where u.id = :id")
    Optional<Long> findGymIdById(@org.springframework.data.repository.query.Param("id") Long id);

    /** Con la fila bloqueada hasta el fin de la transacción: ver GymBlockRepository.findLockedByIdAndGymId. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findLockedByIdAndGymId(Long id, Long gymId);

    /** Usado por MembershipReminderJob: solo socios cuyo último pago cae en la ventana en la que su período (1 mes) vence en 0 a 3 días. */
    List<AppUser> findByRoleAndPaidAtBetween(Role role, java.time.Instant from, java.time.Instant to);

    /** Socios con renovación anticipada alguna vez (V52, period_start no nulo): su vencimiento final no se relaciona con
     *  paid_at, así que no entran por la ventana de arriba. Son pocos (solo quien pagó por adelantado); el job filtra el resto. */
    List<AppUser> findByRoleAndPeriodStartIsNotNull(Role role);

    /** Socios con un cambio de plan programado (V53): los recorre PlanChangeJob. Pocos; respalda el índice parcial. */
    List<AppUser> findByPendingPlanIdIsNotNull();

    /** Para la vista "Administradores" del super-admin — super-admins (sin gym_id) y gym-admins
     *  de todos los gimnasios, cada una en su propia consulta por rol. */
    List<AppUser> findByRole(Role role);

    /** Lo mínimo para revalidar una sesión (UserSessionGuard): una fila liviana, sin hidratar la entidad entera. */
    interface SessionView {
        Role getRole();

        Long getGymId();

        Boolean getActive();

        java.time.Instant getSessionsValidAfter();
    }

    /** Consulta derivada con proyección cerrada: trae solo esas tres columnas ("SessionView" en el nombre es texto libre). */
    Optional<SessionView> findSessionViewById(Long id);
}
