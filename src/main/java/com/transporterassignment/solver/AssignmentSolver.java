package com.transporterassignment.solver;

import com.transporterassignment.model.Dataset;
import com.transporterassignment.model.Solution;

import java.util.Optional;

/**
 * Strategy for assigning every lane to one transporter using at most {@code k} transporters.
 */
public interface AssignmentSolver {

    /**
     * @param data the lanes and quotes
     * @param k    maximum number of transporters to use, at least 1
     * @return the assignment, or empty when no selection of at most {@code k} transporters
     *         (that this solver can find) covers every lane
     */
    Optional<Solution> solve(Dataset data, int k);

    /** Short name reported in API responses, for example {@code EXACT}. */
    String method();
}
