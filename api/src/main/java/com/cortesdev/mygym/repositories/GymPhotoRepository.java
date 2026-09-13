package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymPhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymPhotoRepository extends JpaRepository<GymPhoto, Long> {

    List<GymPhoto> findByGymIdOrderBySortOrderAsc(Long gymId);

    Optional<GymPhoto> findByIdAndGymId(Long id, Long gymId);

    long countByGymId(Long gymId);
}
