package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymBlock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface GymBlockRepository extends JpaRepository<GymBlock, Long> {

    List<GymBlock> findByGymId(Long gymId);

    Optional<GymBlock> findByIdAndGymId(Long id, Long gymId);

    /** Igual que findByIdAndGymId pero con la fila bloqueada hasta el fin de la transacción (SELECT ... FOR UPDATE):
     *  la usa ReservationService.book para que el conteo de cupos no se pise entre reservas simultáneas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GymBlock> findLockedByIdAndGymId(Long id, Long gymId);
}
