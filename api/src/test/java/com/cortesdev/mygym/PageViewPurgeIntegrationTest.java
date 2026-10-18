package com.cortesdev.mygym;

import static org.assertj.core.api.Assertions.assertThat;

import com.cortesdev.mygym.repositories.PageViewRepository;
import com.cortesdev.mygym.services.ExpiredRowsPurgeJob;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** La purga de visitas contra Postgres real: borra las de más de 13 meses por lotes y deja las recientes (V54). */
@SpringBootTest
@Transactional
class PageViewPurgeIntegrationTest {

    @Autowired private PageViewRepository pageViewRepository;
    @Autowired private ExpiredRowsPurgeJob purgeJob;
    @Autowired private EntityManager entityManager;

    private void insert(String createdAtSql) {
        entityManager.createNativeQuery("INSERT INTO page_view (page, created_at) VALUES ('LANDING', " + createdAtSql + ")")
                .executeUpdate();
    }

    @Test
    void deletesOnlyRowsOlderThanThirteenMonths() {
        long before = pageViewRepository.count();
        insert("now() - interval '14 months'");
        insert("now() - interval '400 days'");
        insert("now() - interval '12 months'");
        insert("now()");

        purgeJob.purgeOldPageViews(Instant.now());

        assertThat(pageViewRepository.count()).isEqualTo(before + 2);
    }
}
