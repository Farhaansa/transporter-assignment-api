package com.transporterassignment.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/** Base class for errors that map to a specific HTTP status and a client-safe message. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<String> details;

    protected ApiException(HttpStatus status, String message, List<String> details) {
        super(message);
        this.status = status;
        this.details = List.copyOf(details);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<String> getDetails() {
        return details;
    }
}
