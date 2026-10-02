package com.sopes.backendproyectofinal.shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleBusiness(ApiException exception, HttpServletRequest request) {
        return problem(exception.getStatus(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Revisa los campos indicados.", request);
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    public ProblemDetail handleMalformed(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST,
                "La solicitud contiene datos inválidos. Verifica los tipos y valores enviados.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "La ruta solicitada no existe.", request);
    }

    /**
     * Cerrar el navegador o una pestaña durante la transmisión en vivo es una situación normal.
     * No se registra como error y no se intenta escribir un cuerpo sobre text/event-stream.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException exception) {
        LOGGER.debug("El cliente de la transmisión en vivo se desconectó antes de recibir la actualización.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Error inesperado al procesar la solicitud", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error interno. Intenta nuevamente más tarde.", request);
    }

    private ProblemDetail problem(HttpStatus status, String message, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(switch (status) {
            case BAD_REQUEST -> "Solicitud inválida";
            case NOT_FOUND -> "Registro no encontrado";
            case CONFLICT -> "Operación no permitida";
            case SERVICE_UNAVAILABLE -> "Servicio temporalmente no disponible";
            default -> "Error del servidor";
        });
        detail.setInstance(URI.create(request.getRequestURI()));
        return detail;
    }
}
