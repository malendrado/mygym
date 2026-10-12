package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.MemberFixedClass;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberFixedClassRepository extends JpaRepository<MemberFixedClass, Long> {

    List<MemberFixedClass> findByMemberId(Long memberId);

    /** Batch para la lista de Socios (una sola query por página, nunca una por socio). */
    List<MemberFixedClass> findByMemberIdIn(List<Long> memberIds);

    List<MemberFixedClass> findByGymBlockId(Long gymBlockId);
}
