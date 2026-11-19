package com.transporterassignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "How many transporters may be used at most")
public record AssignmentRequest(
        @Schema(description = "Maximum number of transporters to use (k). Values above the number of transporters are clamped.",
                example = "3")
        @NotNull @Positive Integer maxTransporters) {
}
