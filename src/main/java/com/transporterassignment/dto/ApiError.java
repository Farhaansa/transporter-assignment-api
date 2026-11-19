package com.transporterassignment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;

import java.util.List;

/** The single error body returned by every endpoint. */
public record ApiError(
        String status,
        int code,
        String error,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> details) {

    public static ApiError of(HttpStatusCode statusCode, String message, List<String> details) {
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : "Error";
        return new ApiError("error", statusCode.value(), reason, message, details == null ? List.of() : List.copyOf(details));
    }
}
