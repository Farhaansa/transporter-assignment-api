package com.transporterassignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Lanes and transporter quotes. Replaces any previously saved input.")
public record InputRequest(
        @NotEmpty List<@NotNull @Valid LaneDto> lanes,
        @NotEmpty List<@NotNull @Valid TransporterDto> transporters) {
}
