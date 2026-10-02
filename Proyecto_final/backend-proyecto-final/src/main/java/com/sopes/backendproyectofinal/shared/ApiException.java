package com.sopes.backendproyectofinal.shared;

import org.springframework.http.HttpStatus;

/** Error de negocio con un mensaje seguro para mostrar al usuario. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
