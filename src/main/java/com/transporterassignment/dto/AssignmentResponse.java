package com.transporterassignment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssignmentResponse(
        String status,
        long totalCost,
        @Schema(description = "One entry per lane, ordered by lane id") List<LaneAssignmentDto> assignments,
        @Schema(description = "Transporters that received at least one lane, ordered by id") List<Long> selectedTransporters,
        @Schema(description = "EXACT: exhaustive search, the total cost is the proven minimum. "
                + "GREEDY: heuristic used when the search space exceeds solver.max-combinations; "
                + "the total cost may be higher than the minimum.", example = "EXACT")
        String solver,
        @Schema(description = "Optional remarks, for example clamping of maxTransporters") String note) {
}
