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

    Optional<AppUser> findByIdAndGymId(Long id, Long gymId);
}
