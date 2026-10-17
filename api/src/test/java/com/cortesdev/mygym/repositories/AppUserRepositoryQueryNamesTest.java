package com.cortesdev.mygym.repositories;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.AuthToken;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Payment;
import com.cortesdev.mygym.models.TvCheckinCode;
import com.cortesdev.mygym.models.TvPairingCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * Las pruebas que levantan Spring necesitan la base de datos real y no corren en cualquier máquina. Esta valida,
 * sin base, que los nombres de las consultas derivadas nuevas sean válidos para Spring Data: si alguno no lo fuera,
 * la aplicación entera no arrancaría.
 */
class AppUserRepositoryQueryNamesTest {

    private static void valid(String methodName, Class<?> entity) {
        assertThatCode(() -> new PartTree(methodName, entity)).doesNotThrowAnyException();
    }

    @Test
    void theSessionLookupIsAValidDerivedQueryOverTheEntityId() {
        valid("findSessionViewById", AppUser.class);
    }

    @Test
    void theLockingLookupsUsedByBookingAndTheFlowWebhookAreValid() {
        valid("findLockedByIdAndGymId", GymBlock.class);
        valid("findLockedByIdAndGymId", AppUser.class);
        valid("findLockedByFlowToken", Payment.class);
    }

    @Test
    void theDailyPurgeDeletesAreValid() {
        valid("deleteByExpiresAtBefore", TvPairingCode.class);
        valid("deleteByExpiresAtBefore", TvCheckinCode.class);
        valid("deleteByExpiresAtBefore", AuthToken.class);
    }
}
