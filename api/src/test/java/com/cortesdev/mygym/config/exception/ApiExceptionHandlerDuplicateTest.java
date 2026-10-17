package com.cortesdev.mygym.config.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/** El 409 por restricción única dice lo que realmente pasó (antes siempre decía "Ya tienes una reserva"). */
class ApiExceptionHandlerDuplicateTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException(
                "no se pudo guardar", new RuntimeException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
    }

    @Test
    void aSimultaneousSignUpWithTheSameEmailGetsAnEmailMessageThatRevealsNothingMore() {
        var response = handler.handleDataIntegrityViolation(violation("uk_app_user_email"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getTitle()).isEqualTo("Email duplicado");
        assertThat(response.getBody().getDetail()).contains("ya tiene una cuenta en mygym").doesNotContain("reserva");
    }

    @Test
    void aDuplicatedReservationKeepsItsMessage() {
        var response = handler.handleDataIntegrityViolation(violation("uk_reservation_slot_booked"));

        assertThat(response.getBody().getDetail()).isEqualTo("Ya tienes una reserva para esta clase");
    }

    @Test
    void anyOtherUniqueConstraintNeverClaimsToBeAReservation() {
        var response = handler.handleDataIntegrityViolation(violation("uk_gym_slug"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getDetail()).doesNotContain("reserva");
    }
}
