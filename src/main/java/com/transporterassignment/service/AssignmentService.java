package com.transporterassignment.service;

import com.transporterassignment.dto.AssignmentResponse;
import com.transporterassignment.dto.LaneAssignmentDto;
import com.transporterassignment.exception.InfeasibleAssignmentException;
import com.transporterassignment.exception.NoInputDataException;
import com.transporterassignment.exception.SearchInconclusiveException;
import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;
import com.transporterassignment.repository.DatasetRepository;
import com.transporterassignment.solver.Combinatorics;
import com.transporterassignment.solver.SolverPipeline;
import com.transporterassignment.solver.SolverPipeline.Outcome;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Runs the solver pipeline on the stored input and turns its outcome into a response or an error. */
@Service
public class AssignmentService {

    private final DatasetRepository repository;
    private final SolverPipeline pipeline;

    public AssignmentService(DatasetRepository repository, SolverPipeline pipeline) {
        this.repository = repository;
        this.pipeline = pipeline;
    }

    /**
     * @param maxTransporters the requested limit, at least 1 (checked by request validation)
     */
    public AssignmentResponse assign(int maxTransporters) {
        Dataset data = repository.find().orElseThrow(NoInputDataException::new);
        int n = data.transporters().size();
        int k = Math.min(maxTransporters, n);
        int lanes = data.lanes().size();

        List<String> notes = new ArrayList<>();
        if (maxTransporters > n) {
            notes.add(String.format(Locale.ROOT,
                    "maxTransporters (%d) is greater than the number of transporters (%d); using %d.",
                    maxTransporters, n, n));
        }

        Outcome outcome = pipeline.solve(data, k);
        switch (outcome.kind()) {
            case EXACT:
                return toResponse(outcome.solution().orElseThrow(), notes);
            case PROVEN_INFEASIBLE:
                throw new InfeasibleAssignmentException(String.format(Locale.ROOT,
                        "No selection of at most %d transporter(s) can cover all %d lanes. Increase maxTransporters.",
                        k, lanes));
            case HEURISTIC:
                Solution solution = outcome.solution().orElseThrow();
                notes.add(heuristicNote(outcome, n, k, solution.totalCost()));
                return toResponse(solution, notes);
            default:
                throw new SearchInconclusiveException(String.format(Locale.ROOT,
                        "Search inconclusive: %s before finding or ruling out a cover, and local search found "
                                + "no covering selection of at most %d transporter(s) for all %d lanes within the "
                                + "%,d ms heuristic time budget. One may still exist. Increase maxTransporters, "
                                + "solver.max-search-nodes, solver.max-time-ms or solver.heuristic-time-ms.",
                        exactSearchStop(outcome, n, k), k, lanes, pipeline.heuristicTimeMs()));
        }
    }

    private String heuristicNote(Outcome outcome, int n, int k, long cost) {
        return String.format(Locale.ROOT,
                "%s before proving a minimum, so this is the best answer found by local search within the "
                        + "%,d ms heuristic time budget. It is not proven minimum. %s",
                capitalize(exactSearchStop(outcome, n, k)), pipeline.heuristicTimeMs(),
                lowerBoundGap(cost, outcome.lowerBound()));
    }

    /** For example "the exact search over C(40, 10) = 847,660,528 candidate sets stopped at its budget of ...". */
    private String exactSearchStop(Outcome outcome, int n, int k) {
        String limit = outcome.stoppedAtNodeBudget()
                ? String.format(Locale.ROOT, "its budget of %,d search nodes (solver.max-search-nodes)",
                        pipeline.budget().maxNodes())
                : String.format(Locale.ROOT, "its time budget of %,d ms (solver.max-time-ms)",
                        pipeline.budget().maxTimeMs());
        long sets = Combinatorics.combinations(n, k);
        String space = sets == Long.MAX_VALUE ? "more than 9.2 x 10^18" : String.format(Locale.ROOT, "%,d", sets);
        return String.format(Locale.ROOT, "the exact search over C(%d, %d) = %s candidate sets stopped at %s",
                n, k, space, limit);
    }

    /**
     * The gap to the lower bound is an upper bound on the distance from the optimum, never the distance itself.
     */
    static String lowerBoundGap(long cost, long lowerBound) {
        String bound = "the theoretical lower bound";
        String meaning = " (each lane's cheapest quote, ignoring maxTransporters)";
        if (cost == lowerBound) {
            return String.format(Locale.ROOT, "Total cost %d matches %s %d%s, so no cheaper answer exists; the usage "
                    + "and id tie-breaks are not proven.", cost, bound, lowerBound, meaning);
        }
        if (lowerBound <= 0) {
            return String.format(Locale.ROOT, "Total cost %d is %d above %s %d%s. This bounds how far the answer can "
                    + "be from the optimum; it does not measure that distance.", cost, cost - lowerBound, bound,
                    lowerBound, meaning);
        }
        BigDecimal percent = BigDecimal.valueOf(cost - lowerBound).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(lowerBound), 1, RoundingMode.CEILING);
        return String.format(Locale.ROOT, "Total cost %d is within %s%% of %s %d%s. This bounds how far the answer "
                + "can be from the optimum; it does not measure that distance.", cost, percent.toPlainString(), bound,
                lowerBound, meaning);
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static AssignmentResponse toResponse(Solution solution, List<String> notes) {
        List<LaneAssignmentDto> assignments = solution.laneToTransporter().entrySet().stream()
                .map(e -> new LaneAssignmentDto(e.getKey(), e.getValue()))
                .toList();
        return new AssignmentResponse("success", solution.totalCost(), assignments, solution.selected(),
                solution.method(), notes.isEmpty() ? null : String.join(" ", notes));
    }
}
