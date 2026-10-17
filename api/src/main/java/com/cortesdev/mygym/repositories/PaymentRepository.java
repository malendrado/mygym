package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.Payment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByFlowToken(String flowToken);

    /** Con la fila bloqueada hasta el fin de la transacción: dos webhooks simultáneos del mismo pago se ponen en fila
     *  (el segundo ya ve PAID) en vez de acreditarlo y avisar dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findLockedByFlowToken(String flowToken);

    List<Payment> findByMemberIdIn(List<Long> memberIds);
}
