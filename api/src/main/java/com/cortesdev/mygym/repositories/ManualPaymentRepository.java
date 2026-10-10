package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.ManualPayment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualPaymentRepository extends JpaRepository<ManualPayment, Long> {

    List<ManualPayment> findByGymId(Long gymId);

    List<ManualPayment> findByMemberIdIn(List<Long> memberIds);
}
