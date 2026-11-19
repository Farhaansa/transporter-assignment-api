package com.transporterassignment.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/** 400: the input breaks a cross-field rule (duplicate ids, unknown lanes, repeated quotes). */
public class InvalidInputException extends ApiException {

    public InvalidInputException(String message, List<String> details) {
        super(HttpStatus.BAD_REQUEST, message, details);
    }
}
