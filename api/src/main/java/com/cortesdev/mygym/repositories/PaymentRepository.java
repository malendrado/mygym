package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByFlowToken(String flowToken);
}
