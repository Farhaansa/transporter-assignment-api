package com.transporterassignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "A shipping lane")
public record LaneDto(
        @Schema(description = "Unique lane id", example = "1") @NotNull @Positive Long id,
        @Schema(example = "Mumbai") @NotBlank String origin,
        @Schema(example = "Delhi") @NotBlank String destination) {
}
