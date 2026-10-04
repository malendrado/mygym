package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymClosureBlock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymClosureBlockRepository extends JpaRepository<GymClosureBlock, Long> {

    List<GymClosureBlock> findByClosureId(Long closureId);

    List<GymClosureBlock> findByClosureIdIn(List<Long> closureIds);

    boolean existsByClosureIdAndGymBlockId(Long closureId, Long gymBlockId);
}
