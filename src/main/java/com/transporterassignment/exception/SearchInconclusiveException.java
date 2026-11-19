package com.transporterassignment.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 422, but not a proof: the exact search ran out of budget and the heuristic found no covering selection. A cover
 * may still exist. Kept separate from {@link InfeasibleAssignmentException}, which only a completed exact search
 * may raise.
 */
public class SearchInconclusiveException extends ApiException {

    public SearchInconclusiveException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message, List.of());
    }
}
