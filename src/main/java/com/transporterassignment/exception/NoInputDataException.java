package com.transporterassignment.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/** 409: an assignment was requested before any input was saved. */
public class NoInputDataException extends ApiException {

    public NoInputDataException() {
        super(HttpStatus.CONFLICT,
                "No input data has been saved yet. Call POST /api/v1/transporters/input first.", List.of());
    }
}
