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

    /** Con la fila bloqueada hasta el fin de la transacción: ver GymBlockRepository.findLockedByIdAndGymId. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findLockedByIdAndGymId(Long id, Long gymId);

    /** Usado por MembershipReminderJob — solo socios que alguna vez pagaron. */
    List<AppUser> findByRoleAndPaidAtIsNotNull(Role role);

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
