package com.cortesdev.mygym.config.exception;

import com.cortesdev.mygym.services.exception.AdminNotFoundException;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import com.cortesdev.mygym.services.exception.BrandingSuggestionException;
import com.cortesdev.mygym.services.exception.CapacityExceededException;
import com.cortesdev.mygym.services.exception.DuplicateMemberEmailException;
import com.cortesdev.mygym.services.exception.DemoAccessExpiredException;
import com.cortesdev.mygym.services.exception.DemoSampleMemberMissingException;
import com.cortesdev.mygym.services.exception.DuplicateOwnerEmailException;
import com.cortesdev.mygym.services.exception.DuplicateSlugException;
import com.cortesdev.mygym.services.exception.ForbiddenGymAccessException;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.GymPhotoNotFoundException;
import com.cortesdev.mygym.services.exception.GymPlanNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidBlockScheduleException;
import com.cortesdev.mygym.services.exception.InvalidGoogleTokenException;
import com.cortesdev.mygym.services.exception.InvalidLogoException;
import com.cortesdev.mygym.services.exception.InvalidThemeException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import com.cortesdev.mygym.services.exception.ReservationNotFoundException;
import com.cortesdev.mygym.services.exception.SubscriptionRequiredException;
import com.cortesdev.mygym.services.exception.TooManyGymPhotosException;
import com.cortesdev.mygym.services.exception.UnauthorizedGoogleLoginException;
import java.net.URI;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

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

    @ExceptionHandler(InvalidThemeException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTheme(InvalidThemeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Theme inválido");
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

    @ExceptionHandler(DuplicateMemberEmailException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateMemberEmail(DuplicateMemberEmailException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Email de socio duplicado");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DuplicateOwnerEmailException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateOwnerEmail(DuplicateOwnerEmailException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Email duplicado");
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
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Ya tienes una reserva para esta clase");
        problem.setTitle("Reserva duplicada");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");
        problem.setTitle("Error interno del servidor");
        problem.setType(URI.create("about:blank"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
