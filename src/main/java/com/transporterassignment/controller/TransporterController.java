package com.transporterassignment.controller;

import com.transporterassignment.config.ApiExamples;
import com.transporterassignment.dto.ApiError;
import com.transporterassignment.dto.AssignmentRequest;
import com.transporterassignment.dto.AssignmentResponse;
import com.transporterassignment.dto.InputRequest;
import com.transporterassignment.dto.InputResponse;
import com.transporterassignment.service.AssignmentService;
import com.transporterassignment.service.InputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/transporters", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Transporter assignment",
        description = "Save lanes and quotes, then assign every lane to one transporter at the lowest cost found")
public class TransporterController {

    private final InputService inputService;
    private final AssignmentService assignmentService;

    public TransporterController(InputService inputService, AssignmentService assignmentService) {
        this.inputService = inputService;
        this.assignmentService = assignmentService;
    }

    @PostMapping(path = "/input", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Save lanes and transporter quotes",
            description = "Replaces any previously saved input. Rejected input leaves the previous data in place.")
    @ApiResponse(responseCode = "200", description = "Input saved")
    @ApiResponse(responseCode = "400", description = "Malformed or inconsistent input",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "A lane has no quote from any transporter",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public InputResponse saveInput(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "Test Case 1", value = ApiExamples.CASE_1),
                    @ExampleObject(name = "Test Case 2", value = ApiExamples.CASE_2)}))
            @Valid @RequestBody InputRequest request) {
        return inputService.replaceInput(request);
    }

    @PostMapping(path = "/assignment", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Assign every lane using at most maxTransporters transporters, minimizing total cost",
            description = """
                    Priority (our documented interpretation of the brief): lowest total cost first; among \
                    minimum-cost answers, as many transporters as possible up to maxTransporters; remaining ties \
                    by lower transporter ids.

                    The 'solver' field says how the result was produced. EXACT: exhaustive search, the total cost \
                    is the proven minimum. GREEDY: used only when C(N, k) exceeds solver.max-combinations, because \
                    the problem is NP-hard; it is a heuristic, its cost may be higher than the minimum, and a note \
                    says so.""")
    @ApiResponse(responseCode = "200", description = "Assignment found")
    @ApiResponse(responseCode = "400", description = "maxTransporters missing or not positive",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "No input saved yet",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "EXACT: no selection of at most maxTransporters covers every "
            + "lane. GREEDY: the heuristic found no cover, but one may still exist.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AssignmentResponse assign(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "k = 3", value = "{\"maxTransporters\": 3}"),
                    @ExampleObject(name = "k = 2 (422 for Test Case 2)", value = "{\"maxTransporters\": 2}")}))
            @Valid @RequestBody AssignmentRequest request) {
        return assignmentService.assign(request.maxTransporters());
    }
}
