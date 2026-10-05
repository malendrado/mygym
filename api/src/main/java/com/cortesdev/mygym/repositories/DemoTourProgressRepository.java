package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.DemoTourProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoTourProgressRepository extends JpaRepository<DemoTourProgress, Long> {

    Optional<DemoTourProgress> findByDemoAdminIdAndTour(Long demoAdminId, String tour);

    /** Para resolver el progreso de varios admins a la vez (listado del super-admin) sin N+1. */
    List<DemoTourProgress> findByDemoAdminIdIn(List<Long> demoAdminIds);
}
