package com.transporterassignment.service;

import com.transporterassignment.dto.AssignmentResponse;
import com.transporterassignment.dto.LaneAssignmentDto;
import com.transporterassignment.exception.InfeasibleAssignmentException;
import com.transporterassignment.exception.NoInputDataException;
import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import com.transporterassignment.repository.DatasetRepository;
import com.transporterassignment.solver.SolverSelector;
import com.transporterassignment.solver.SolverSelector.SolverChoice;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Runs the chosen solver on the stored input and shapes the result for the API. */
@Service
public class AssignmentService {

    private final DatasetRepository repository;
    private final SolverSelector selector;

    public AssignmentService(DatasetRepository repository, SolverSelector selector) {
        this.repository = repository;
        this.selector = selector;
    }

    /**
     * @param maxTransporters the requested limit, at least 1 (checked by request validation)
     */
    public AssignmentResponse assign(int maxTransporters) {
        Dataset data = repository.find().orElseThrow(NoInputDataException::new);
        int n = data.transporters().size();
        int k = Math.min(maxTransporters, n);

        List<String> notes = new ArrayList<>();
        if (maxTransporters > n) {
            notes.add(String.format(Locale.ROOT,
                    "maxTransporters (%d) is greater than the number of transporters (%d); using %d.",
                    maxTransporters, n, n));
        }

        SolverChoice choice = selector.select(n, k);
        if (!choice.exhaustive()) {
            notes.add(String.format(Locale.ROOT,
                    "The search space of C(%d, %d) = %s combinations exceeds the limit of %,d, so a greedy heuristic "
                            + "was used instead of the exact search. The result may not be optimal: the total cost "
                            + "may be higher than the minimum.",
                    n, k, describe(choice.searchSpace()), choice.maxCombinations()));
        }

        Solution solution = choice.solver().solve(data, k)
                .orElseThrow(() -> infeasible(k, data.lanes().size(), choice.exhaustive()));
        return toResponse(solution, notes);
    }

    private static InfeasibleAssignmentException infeasible(int k, int laneCount, boolean exhaustive) {
        if (exhaustive) {
            return new InfeasibleAssignmentException(String.format(Locale.ROOT,
                    "No selection of at most %d transporter(s) can cover all %d lanes. Increase maxTransporters.",
                    k, laneCount));
        }
        return new InfeasibleAssignmentException(String.format(Locale.ROOT,
                "The greedy heuristic found no selection of at most %d transporter(s) that covers all %d lanes "
                        + "(one may still exist). Increase maxTransporters or solver.max-combinations.",
                k, laneCount));
    }

    private static AssignmentResponse toResponse(Solution solution, List<String> notes) {
        List<LaneAssignmentDto> assignments = solution.laneToTransporter().entrySet().stream()
                .map(e -> new LaneAssignmentDto(e.getKey(), e.getValue()))
                .toList();
        return new AssignmentResponse("success", solution.totalCost(), assignments, solution.selected(),
                solution.method(), notes.isEmpty() ? null : String.join(" ", notes));
    }

    private static String describe(long combinations) {
        return combinations == Long.MAX_VALUE
                ? "more than 9.2 x 10^18"
                : String.format(Locale.ROOT, "%,d", combinations);
    }
}
