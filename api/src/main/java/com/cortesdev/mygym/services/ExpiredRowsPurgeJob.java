package com.cortesdev.mygym.services;

import com.cortesdev.mygym.repositories.AuthTokenRepository;
import com.cortesdev.mygym.repositories.PageViewRepository;
import com.cortesdev.mygym.repositories.TvCheckinCodeRepository;
import com.cortesdev.mygym.repositories.TvPairingCodeRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra a diario las filas efímeras ya vencidas. Hasta la auditoría de 2026-10-17 ninguna se limpiaba nunca: los
 * códigos de emparejamiento y de check-in de la TV (cada llamada pública inserta uno) y los tokens de invitación,
 * reseteo y alta se acumulaban para siempre. De paso los códigos de emparejamiento ya reclamados dejan de guardar
 * el token de la pantalla en claro. Las visitas de la landing (page_view) también se purgan, pero solo las de más de 13 meses (ver purgeOldPageViews): una
 * ventana larga para que la analítica compare año contra año sin que la tabla crezca para siempre.
 */
@Component
@RequiredArgsConstructor
public class ExpiredRowsPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredRowsPurgeJob.class);

    // Un margen después del vencimiento por si hay que mirar qué pasó con un código o enlace reciente.
    static final Duration CODES_GRACE = Duration.ofDays(1);
    static final Duration TOKENS_GRACE = Duration.ofDays(7);

    // 13 meses: un año completo más un mes de margen para comparar contra el mismo mes del año pasado.
    static final int PAGE_VIEW_RETENTION_MONTHS = 13;
    // Lotes chicos para no tener un DELETE gigante con la transacción abierta ni inflar el WAL de golpe.
    static final int PAGE_VIEW_BATCH_SIZE = 5000;
    // Tope de lotes por corrida: si hay un atraso enorme sigue al día siguiente en vez de trabarse horas.
    static final int PAGE_VIEW_MAX_BATCHES = 200;

    private final TvPairingCodeRepository pairingCodeRepository;
    private final TvCheckinCodeRepository checkinCodeRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PageViewRepository pageViewRepository;

    @Scheduled(cron = "0 30 4 * * *", zone = "America/Santiago")
    @Transactional
    public void purge() {
        purgeAt(Instant.now());
    }

    /**
     * Aparte de purge() y sin @Transactional a propósito: cada lote es su propia transacción corta (la query del
     * repositorio), así no se mantiene una transacción larga abierta borrando miles de filas.
     */
    @Scheduled(cron = "0 45 4 * * *", zone = "America/Santiago")
    public void purgePageViews() {
        purgeOldPageViews(Instant.now());
    }

    public void purgeOldPageViews(Instant now) {
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(PAGE_VIEW_RETENTION_MONTHS).toInstant();
        long total = 0;
        for (int i = 0; i < PAGE_VIEW_MAX_BATCHES; i++) {
            int deleted = pageViewRepository.deleteOlderThanBatch(cutoff, PAGE_VIEW_BATCH_SIZE);
            total += deleted;
            if (deleted < PAGE_VIEW_BATCH_SIZE) {
                break;
            }
        }
        log.info("Limpieza de visitas: {} filas de page_view anteriores a {}", total, cutoff);
    }

    void purgeAt(Instant now) {
        long pairing = pairingCodeRepository.deleteByExpiresAtBefore(now.minus(CODES_GRACE));
        long checkin = checkinCodeRepository.deleteByExpiresAtBefore(now.minus(CODES_GRACE));
        long tokens = authTokenRepository.deleteByExpiresAtBefore(now.minus(TOKENS_GRACE));
        log.info(
                "Limpieza de filas vencidas: {} códigos de emparejamiento, {} de check-in, {} tokens de acceso",
                pairing, checkin, tokens);
    }
}
