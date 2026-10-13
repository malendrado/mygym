package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.PushSubscription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserId(Long userId);

    List<PushSubscription> findByUserIdIn(Collection<Long> userIds);

    long deleteByEndpointAndUserId(String endpoint, Long userId);

    long deleteByUserId(Long userId);
}
