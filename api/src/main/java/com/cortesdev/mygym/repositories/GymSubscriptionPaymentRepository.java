package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymSubscriptionPayment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface GymSubscriptionPaymentRepository extends JpaRepository<GymSubscriptionPayment, Long> {

    List<GymSubscriptionPayment> findTop50ByGymIdOrderByCreatedAtDescIdDesc(Long gymId);

    boolean existsByGymIdAndStatus(Long gymId, String status);

    Optional<GymSubscriptionPayment> findByIdAndGymId(Long id, Long gymId);

    /** Fila bloqueada: dos avisos simultaneos del mismo pago (webhook repetido) se acreditan una sola vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GymSubscriptionPayment> findLockedByFlowToken(String flowToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GymSubscriptionPayment> findLockedByIdAndGymId(Long id, Long gymId);

    Optional<GymSubscriptionPayment> findFirstByGymIdAndStatusOrderByIdDesc(Long gymId, String status);
}
