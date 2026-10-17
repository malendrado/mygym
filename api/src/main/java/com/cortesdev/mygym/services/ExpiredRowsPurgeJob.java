package com.cortesdev.mygym.services;

import com.cortesdev.mygym.repositories.AuthTokenRepository;
import com.cortesdev.mygym.repositories.TvCheckinCodeRepository;
import com.cortesdev.mygym.repositories.TvPairingCodeRepository;
import java.time.Duration;
import java.time.Instant;
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
 * el token de la pantalla en claro. NO toca page_view: son las visitas que mide la analítica.
 */
@Component
@RequiredArgsConstructor
public class ExpiredRowsPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredRowsPurgeJob.class);

    // Un margen después del vencimiento por si hay que mirar qué pasó con un código o enlace reciente.
    static final Duration CODES_GRACE = Duration.ofDays(1);
    static final Duration TOKENS_GRACE = Duration.ofDays(7);

    private final TvPairingCodeRepository pairingCodeRepository;
    private final TvCheckinCodeRepository checkinCodeRepository;
    private final AuthTokenRepository authTokenRepository;

    @Scheduled(cron = "0 30 4 * * *", zone = "America/Santiago")
    @Transactional
    public void purge() {
        purgeAt(Instant.now());
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
