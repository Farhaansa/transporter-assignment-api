package com.transporterassignment.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/** 422: the input is well formed, but no assignment can cover every lane. */
public class InfeasibleAssignmentException extends ApiException {

    public InfeasibleAssignmentException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message, List.of());
    }
}
