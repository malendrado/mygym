package com.cortesdev.mygym.config.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.services.EmailOwnerDescriber;
import com.cortesdev.mygym.services.exception.DuplicateMemberEmailException;
import com.cortesdev.mygym.services.exception.DuplicateOwnerEmailException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Solo el super admin ve dónde vive un email rechazado por duplicado; cualquier otro rol sigue viendo el mensaje genérico. */
class ApiExceptionHandlerDuplicateEmailDetailTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final EmailOwnerDescriber describer = mock(EmailOwnerDescriber.class);

    @BeforeEach
    void setUp() {
        handler.setEmailOwnerDescriber(describer);
        when(describer.describe("ana@gym.cl")).thenReturn(Optional.of("socio del gimnasio «XaloGym» (id 60)"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", List.of(() -> "ROLE_" + role)));
    }

    @Test
    void theSuperAdminSeesWhereTheEmailLives() {
        loginAs("SUPER_ADMIN");

        var owner = handler.handleDuplicateOwnerEmail(new DuplicateOwnerEmailException("ana@gym.cl"));
        var member = handler.handleDuplicateMemberEmail(new DuplicateMemberEmailException("ana@gym.cl"));

        assertThat(owner.getBody().getDetail()).contains("es socio del gimnasio «XaloGym» (id 60)");
        assertThat(member.getBody().getDetail()).contains("es socio del gimnasio «XaloGym» (id 60)");
    }

    @Test
    void aGymAdminKeepsTheGenericMessageAndNothingIsLookedUp() {
        loginAs("GYM_ADMIN");

        var owner = handler.handleDuplicateOwnerEmail(new DuplicateOwnerEmailException("ana@gym.cl"));

        assertThat(owner.getBody().getDetail()).doesNotContain("XaloGym").contains("ya tiene una cuenta en mygym");
    }

    @Test
    void withoutASessionTheMessageIsGeneric() {
        var member = handler.handleDuplicateMemberEmail(new DuplicateMemberEmailException("ana@gym.cl"));

        assertThat(member.getBody().getDetail()).doesNotContain("XaloGym");
    }

    @Test
    void theSpecificMessagesOfOneGymAreNotReplaced() {
        loginAs("SUPER_ADMIN");

        var response = handler.handleDuplicateOwnerEmail(DuplicateOwnerEmailException.alreadyAdminOfThisGym("ana@gym.cl"));

        assertThat(response.getBody().getDetail()).contains("ya es administrador de tu gimnasio").doesNotContain("XaloGym");
    }
}
