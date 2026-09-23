package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymDeletionAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymDeletionAuditRepository extends JpaRepository<GymDeletionAudit, Long> {

    List<GymDeletionAudit> findAllByOrderByExecutedAtDesc();
}
