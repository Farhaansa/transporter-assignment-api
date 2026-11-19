package com.transporterassignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

@Schema(description = "A transporter and the lanes it quoted")
public record TransporterDto(
        @Schema(description = "Unique transporter id", example = "1") @NotNull @Positive Long id,
        @Schema(example = "Transporter T1") @NotBlank String name,
        @Schema(description = "Quotes for the lanes this transporter can serve")
        @NotNull List<@NotNull @Valid LaneQuoteDto> laneQuotes) {
}
