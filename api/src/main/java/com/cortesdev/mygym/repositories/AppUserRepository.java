package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<AppUser> findByGymIdAndRole(Long gymId, Role role);

    /** Buscador por nombre para el admin (cancelar la reserva de un socio puntual). */
    List<AppUser> findByGymIdAndRoleAndNameContainingIgnoreCase(Long gymId, Role role, String name);

    Optional<AppUser> findByIdAndGymId(Long id, Long gymId);

    /** Usado por MembershipReminderJob — solo socios que alguna vez pagaron. */
    List<AppUser> findByRoleAndPaidAtIsNotNull(Role role);
}
