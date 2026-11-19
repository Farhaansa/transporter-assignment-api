package com.transporterassignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "A transporter's price for one lane")
public record LaneQuoteDto(
        @Schema(description = "Id of a lane in the same request", example = "1") @NotNull Long laneId,
        @Schema(description = "Whole-number price, greater than 0 and at most 10^12", example = "20835")
        @NotNull @Positive @Max(LaneQuoteDto.MAX_QUOTE) Long quote) {

    /** Upper bound that keeps every total far from long overflow. */
    public static final long MAX_QUOTE = 1_000_000_000_000L;
}
