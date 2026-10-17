package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.TvPairingCode;
import com.cortesdev.mygym.models.dto.TvPairingStatusResponse;
import com.cortesdev.mygym.repositories.TvPairingCodeRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** El código de emparejamiento se ve en una pantalla pública: el token de la TV solo se entrega justo tras reclamarlo. */
@ExtendWith(MockitoExtension.class)
class TvScreenServicePairingTest {

    @Mock private TvPairingCodeRepository pairingCodeRepository;
    @InjectMocks private TvScreenService tvScreenService;

    private TvPairingCode pairing(Instant claimedAt) {
        return TvPairingCode.builder()
                .code("ABC234")
                .createdAt(Instant.now().minusSeconds(300))
                .expiresAt(Instant.now().plusSeconds(300))
                .claimedAt(claimedAt)
                .screenToken(claimedAt != null ? "token-secreto" : null)
                .build();
    }

    @Test
    void anUnclaimedCodeDeliversNoToken() {
        when(pairingCodeRepository.findByCode("ABC234")).thenReturn(Optional.of(pairing(null)));

        TvPairingStatusResponse status = tvScreenService.pairingStatus("ABC234");

        assertThat(status.claimed()).isFalse();
        assertThat(status.screenToken()).isNull();
    }

    @Test
    void justAfterTheAdminClaimsTheCodeTheTvReceivesTheToken() {
        when(pairingCodeRepository.findByCode("ABC234")).thenReturn(Optional.of(pairing(Instant.now().minusSeconds(5))));

        TvPairingStatusResponse status = tvScreenService.pairingStatus("ABC234");

        assertThat(status.claimed()).isTrue();
        assertThat(status.screenToken()).isEqualTo("token-secreto");
    }

    @Test
    void onceTheDeliveryWindowPassesAnyoneWhoSawTheCodeGetsNoToken() {
        Instant longAgo = Instant.now().minus(TvScreenService.TOKEN_DELIVERY_WINDOW).minusSeconds(5);
        when(pairingCodeRepository.findByCode("ABC234")).thenReturn(Optional.of(pairing(longAgo)));

        TvPairingStatusResponse status = tvScreenService.pairingStatus("ABC234");

        assertThat(status.claimed()).isTrue();
        assertThat(status.screenToken()).isNull();
    }
}
