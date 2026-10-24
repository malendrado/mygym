package com.cortesdev.mygym.config.exception;

import com.cortesdev.mygym.services.EmailOwnerDescriber;
import com.cortesdev.mygym.services.exception.AdminNotFoundException;
import com.cortesdev.mygym.services.exception.AlreadyCheckedInException;
import com.cortesdev.mygym.services.exception.AttendeesHiddenException;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import com.cortesdev.mygym.services.exception.BrandingSuggestionException;
import com.cortesdev.mygym.services.exception.CapacityExceededException;
import com.cortesdev.mygym.services.exception.CheckinCodeNotFoundException;
import com.cortesdev.mygym.services.exception.DuplicateMemberEmailException;
import com.cortesdev.mygym.services.exception.DemoAccessExpiredException;
import com.cortesdev.mygym.services.exception.DemoSampleMemberMissingException;
import com.cortesdev.mygym.services.exception.GymDisconnectionEmailFailedException;
import com.cortesdev.mygym.services.exception.GymNameMismatchException;
import com.cortesdev.mygym.services.exception.AdminRemovalNotAllowedException;
import com.cortesdev.mygym.services.exception.DuplicateOwnerEmailException;
import com.cortesdev.mygym.services.exception.DuplicateSlugException;
import com.cortesdev.mygym.services.exception.FlowNotConfiguredException;
import com.cortesdev.mygym.services.exception.ForbiddenGymAccessException;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.GymClosedException;
import com.cortesdev.mygym.services.exception.GymClosureNotFoundException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidClosureRequestException;
import com.cortesdev.mygym.services.exception.GymLimitReachedException;
import com.cortesdev.mygym.services.exception.InvalidAttendanceException;
import com.cortesdev.mygym.services.exception.InvalidPushSubscriptionException;
import com.cortesdev.mygym.services.exception.InvalidPaymentException;
import com.cortesdev.mygym.services.exception.InvalidFixedClassException;
import com.cortesdev.mygym.services.exception.InvalidMemberProfileException;
import com.cortesdev.mygym.services.exception.ProfileIncompleteException;
import com.cortesdev.mygym.services.exception.GymPhotoNotFoundException;
import com.cortesdev.mygym.services.exception.GymPlanNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidBlockScheduleException;
import com.cortesdev.mygym.services.exception.InvalidCredentialsException;
import com.cortesdev.mygym.services.exception.InvalidGoogleTokenException;
import com.cortesdev.mygym.services.exception.InvalidLogoException;
import com.cortesdev.mygym.services.exception.InvalidOrExpiredTokenException;
import com.cortesdev.mygym.services.exception.InvalidRutException;
import com.cortesdev.mygym.services.exception.InvalidSubscriptionException;
import com.cortesdev.mygym.services.exception.RenewalWindowException;
import com.cortesdev.mygym.services.exception.InvalidThemeException;
import com.cortesdev.mygym.services.exception.SubscriptionConflictException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import com.cortesdev.mygym.services.exception.MonthlyQuotaExceededException;
import com.cortesdev.mygym.services.exception.ReservationNotFoundException;
import com.cortesdev.mygym.services.exception.NoActiveReservationException;
import com.cortesdev.mygym.services.exception.InvalidWorkoutLogException;
import com.cortesdev.mygym.services.exception.ProfesorNotFoundException;
import com.cortesdev.mygym.services.exception.SubscriptionRequiredException;
import com.cortesdev.mygym.services.exception.TooManyGymPhotosException;
import com.cortesdev.mygym.services.exception.TvPairingCodeNotFoundException;
import com.cortesdev.mygym.services.exception.TvScreenNotFoundException;
import com.cortesdev.mygym.services.exception.UnauthorizedGoogleLoginException;
import com.cortesdev.mygym.services.exception.WorkoutLogNotFoundException;
import com.cortesdev.mygym.services.exception.WorkoutPlanNotFoundException;
import java.net.URI;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // Opcional (setter) para no cambiar el constructor: las pruebas crean el manejador con `new ApiExceptionHandler()`.
    private EmailOwnerDescriber emailOwnerDescriber;

    @Autowired(required = false)
    void setEmailOwnerDescriber(EmailOwnerDescriber emailOwnerDescriber) {
        this.emailOwnerDescriber = emailOwnerDescriber;
    }

    /**
     * Al super admin se le dice dónde vive el email que rechazó ("es socio del gimnasio «X»"); a cualquier otro rol se le deja
     * el mensaje genérico, para no revelar de qué gimnasio ni con qué rol es una cuenta ajena.
     */
    private String duplicateEmailDetail(String email, String genericMessage) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean superAdmin = auth != null
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (!superAdmin || emailOwnerDescriber == null || email == null) {
            return genericMessage;
        }
        return emailOwnerDescriber
                .describe(email)
                .map(where -> "El email " + email + " ya existe en mygym: es " + where + ". Usa otro email.")
                .orElse(genericMessage);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Los datos ingresados no son válidos");
        problem.setTitle("Solicitud inválida");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty(
                "errors",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(FieldError::getDefaultMessage)
                        .toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(GymNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleGymNotFound(GymNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Gimnasio no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(GymBlockNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleGymBlockNotFound(GymBlockNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Bloque no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(TvPairingCodeNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTvPairingCodeNotFound(TvPairingCodeNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Código de emparejamiento no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(TvScreenNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTvScreenNotFound(TvScreenNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Pantalla no encontrada");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(CheckinCodeNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleCheckinCodeNotFound(CheckinCodeNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Código de asistencia no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(NoActiveReservationException.class)
    public ResponseEntity<ProblemDetail> handleNoActiveReservation(NoActiveReservationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Sin reserva activa");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AlreadyCheckedInException.class)
    public ResponseEntity<ProblemDetail> handleAlreadyCheckedIn(AlreadyCheckedInException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Asistencia ya marcada");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(GymPhotoNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleGymPhotoNotFound(GymPhotoNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Foto no encontrada");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(GymPlanNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleGymPlanNotFound(GymPlanNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Plan no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(DuplicateSlugException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateSlug(DuplicateSlugException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Slug duplicado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidBlockScheduleException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSchedule(InvalidBlockScheduleException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Horario de bloque inválido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(RenewalWindowException.class)
    public ResponseEntity<ProblemDetail> handleRenewalWindow(RenewalWindowException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Todavía no puedes renovar");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidSubscriptionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSubscription(InvalidSubscriptionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Suscripción inválida");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(SubscriptionConflictException.class)
    public ResponseEntity<ProblemDetail> handleSubscriptionConflict(SubscriptionConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Suscripción en otro estado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidThemeException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTheme(InvalidThemeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Theme inválido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InvalidRutException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRut(InvalidRutException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("RUT inválido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleMemberNotFound(MemberNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Socio no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleReservationNotFound(ReservationNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Reserva no encontrada");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(WorkoutPlanNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleWorkoutPlanNotFound(WorkoutPlanNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Rutina no encontrada");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(WorkoutLogNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleWorkoutLogNotFound(WorkoutLogNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Registro no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(InvalidWorkoutLogException.class)
    public ResponseEntity<ProblemDetail> handleInvalidWorkoutLog(InvalidWorkoutLogException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Registro inválido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ProfesorNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleProfesorNotFound(ProfesorNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Profesor no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(GymLimitReachedException.class)
    public ResponseEntity<ProblemDetail> handleGymLimitReached(GymLimitReachedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Límite del gimnasio alcanzado");
        problem.setProperty("code", "LIMIT_" + ex.kind().name());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DuplicateMemberEmailException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateMemberEmail(DuplicateMemberEmailException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, duplicateEmailDetail(ex.getEmail(), ex.getMessage()));
        problem.setTitle("Email de socio duplicado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DuplicateOwnerEmailException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateOwnerEmail(DuplicateOwnerEmailException ex) {
        String detail = ex.isGeneric() ? duplicateEmailDetail(ex.getEmail(), ex.getMessage()) : ex.getMessage();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setTitle("Email duplicado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AdminRemovalNotAllowedException.class)
    public ResponseEntity<ProblemDetail> handleAdminRemovalNotAllowed(AdminRemovalNotAllowedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("No se puede quitar al administrador");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidLogoException.class)
    public ResponseEntity<ProblemDetail> handleInvalidLogo(InvalidLogoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Logo inválido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(CapacityExceededException.class)
    public ResponseEntity<ProblemDetail> handleCapacityExceeded(CapacityExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Cupo excedido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(BookingWindowClosedException.class)
    public ResponseEntity<ProblemDetail> handleBookingWindowClosed(BookingWindowClosedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Ventana de reserva cerrada");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(SubscriptionRequiredException.class)
    public ResponseEntity<ProblemDetail> handleSubscriptionRequired(SubscriptionRequiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        problem.setTitle("Membresía requerida");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(problem);
    }

    @ExceptionHandler(FlowNotConfiguredException.class)
    public ResponseEntity<ProblemDetail> handleFlowNotConfigured(FlowNotConfiguredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Flow no configurado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(UnauthorizedGoogleLoginException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorizedGoogleLogin(UnauthorizedGoogleLoginException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Cuenta no registrada");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidGoogleToken(InvalidGoogleTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Token de Google inválido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Credenciales inválidas");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    // BAD_REQUEST, no GONE: 410 ya está tomado globalmente por el interceptor del frontend para
    // "demo vencida" (DemoAccessExpiredException) — ese interceptor no filtra por URL, así que
    // reusar 410 acá haría que un token de activación vencido cerrara la sesión del usuario y lo
    // mandara al formulario de contacto de la demo. Nada que ver.
    @ExceptionHandler(InvalidOrExpiredTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrExpiredToken(InvalidOrExpiredTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Link inválido o vencido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(BrandingSuggestionException.class)
    public ResponseEntity<ProblemDetail> handleBrandingSuggestion(BrandingSuggestionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Falló la sugerencia de marca");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

    @ExceptionHandler(AdminNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAdminNotFound(AdminNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Administrador no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(AttendeesHiddenException.class)
    public ResponseEntity<ProblemDetail> handleAttendeesHidden(AttendeesHiddenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Lista de asistentes no disponible");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(ForbiddenGymAccessException.class)
    public ResponseEntity<ProblemDetail> handleForbiddenGymAccess(ForbiddenGymAccessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Acceso denegado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(TooManyGymPhotosException.class)
    public ResponseEntity<ProblemDetail> handleTooManyGymPhotos(TooManyGymPhotosException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Demasiadas fotos");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // 410 (no 401/403): un status propio para que el frontend distinga "tu demo venció, te
    // mandamos al contacto" de un error de auth genérico sin tener que parsear el texto.
    @ExceptionHandler(GymNameMismatchException.class)
    public ResponseEntity<ProblemDetail> handleGymNameMismatch(GymNameMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Nombre no coincide");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(GymDisconnectionEmailFailedException.class)
    public ResponseEntity<ProblemDetail> handleGymDisconnectionEmailFailed(GymDisconnectionEmailFailedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("No se pudo notificar al administrador");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

    @ExceptionHandler(DemoAccessExpiredException.class)
    public ResponseEntity<ProblemDetail> handleDemoAccessExpired(DemoAccessExpiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        problem.setTitle("Demo vencida");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.GONE).body(problem);
    }

    @ExceptionHandler(DemoSampleMemberMissingException.class)
    public ResponseEntity<ProblemDetail> handleDemoSampleMemberMissing(DemoSampleMemberMissingException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Demo sin sembrar");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // Catches the race where two requests for the same member+slot both pass the
        // service-layer duplicate check and hit the DB unique index at the same time.
        // Dos personas guardando a la vez las clases fijas del mismo socio (uk_member_fixed_class, V37).
        String root = ex.getMostSpecificCause().getMessage();
        if (root != null && root.contains("uk_member_fixed_class")) {
            ProblemDetail fixed = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "Otra persona está guardando las clases fijas de este socio en este momento. Vuelve a intentarlo.");
            fixed.setTitle("Clases fijas en uso");
            fixed.setProperty("timestamp", Instant.now());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(fixed);
        }
        // Dos altas simultáneas con el mismo email: la segunda cae en uk_app_user_email. Mensaje genérico a propósito
        // (no dice de qué gimnasio ni de qué rol es la cuenta existente).
        if (root != null && root.contains("uk_app_user_email")) {
            ProblemDetail email = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "Ese email ya tiene una cuenta en mygym. Usa otro email.");
            email.setTitle("Email duplicado");
            email.setProperty("timestamp", Instant.now());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(email);
        }
        if (root != null && root.contains("uk_reservation_slot")) {
            ProblemDetail problem =
                    ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Ya tienes una reserva para esta clase");
            problem.setTitle("Reserva duplicada");
            problem.setProperty("timestamp", Instant.now());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }
        // Cualquier otra restricción única (o una carrera que no conocemos): no se afirma nada falso sobre reservas.
        ProblemDetail other = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Ese registro ya existe o alguien lo acaba de crear. Actualiza e inténtalo de nuevo.");
        other.setTitle("Registro duplicado");
        other.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(other);
    }

    @ExceptionHandler(GymClosureNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleGymClosureNotFound(GymClosureNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Cierre no encontrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(GymClosedException.class)
    public ResponseEntity<ProblemDetail> handleGymClosed(GymClosedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Gimnasio cerrado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(MonthlyQuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleMonthlyQuotaExceeded(MonthlyQuotaExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Cupo mensual agotado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidMemberProfileException.class)
    public ResponseEntity<ProblemDetail> handleInvalidMemberProfile(InvalidMemberProfileException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Datos inválidos");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ProfileIncompleteException.class)
    public ResponseEntity<ProblemDetail> handleProfileIncomplete(ProfileIncompleteException ex) {
        // 409 y no 403: el interceptor del frontend convierte todo 403 de un POST/PUT en "esta es una
        // demo de solo lectura", que aquí sería un mensaje falso.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Perfil incompleto");
        problem.setProperty("code", "PROFILE_INCOMPLETE");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // Un parámetro con formato inválido (ej. weekday=FOO, una fecha mal escrita) es un 400, no un 500.
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleArgumentTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El valor de «" + ex.getName() + "» no es válido.");
        problem.setTitle("Parámetro inválido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InvalidPaymentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPayment(InvalidPaymentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Pago inválido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(com.cortesdev.mygym.services.exception.PaymentConflictException.class)
    public ResponseEntity<ProblemDetail> handlePaymentConflict(
            com.cortesdev.mygym.services.exception.PaymentConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Pago en conflicto");
        problem.setProperty("code", ex.getCode());
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidAttendanceException.class)
    public ResponseEntity<ProblemDetail> handleInvalidAttendance(InvalidAttendanceException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Asistencia inválida");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InvalidPushSubscriptionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPushSubscription(InvalidPushSubscriptionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Suscripción de avisos inválida");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InvalidFixedClassException.class)
    public ResponseEntity<ProblemDetail> handleInvalidFixedClass(InvalidFixedClassException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Clase fija inválida");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InvalidClosureRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidClosureRequest(InvalidClosureRequestException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Cierre inválido");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Error no controlado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");
        problem.setTitle("Error interno del servidor");
        problem.setType(URI.create("about:blank"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
