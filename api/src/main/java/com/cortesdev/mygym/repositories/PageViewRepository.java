package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.PageView;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageViewRepository extends JpaRepository<PageView, Long> {

    long countByPage(String page);

    long countByPageAndCreatedAtAfter(String page, Instant since);

    List<PageView> findByPageAndGymIdIsNotNull(String page);

    List<PageView> findByPageAndGymIdIsNotNullAndCreatedAtAfter(String page, Instant since);
}
