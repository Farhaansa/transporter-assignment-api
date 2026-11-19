package com.transporterassignment.solver;

import com.transporterassignment.TestData;
import com.transporterassignment.model.Dataset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SolverPipelineTest {

    private static SolverPipeline pipeline(long maxNodes) {
        return new SolverPipeline(new ExactSubsetSolver(), new LocalSearchSolver(3_000), maxNodes, 10_000);
    }

    @Test
    @DisplayName("Default budget: sample data is solved exactly")
    void exactWithinBudget() {
        SolverPipeline.Outcome outcome = pipeline(5_000_000).solve(TestData.case1(), 3);

        assertThat(outcome.kind()).isEqualTo(SolverPipeline.Kind.EXACT);
        assertThat(outcome.solution().orElseThrow().totalCost()).isEqualTo(60139);
        assertThat(outcome.solution().orElseThrow().method()).isEqualTo("EXACT");
        assertThat(outcome.lowerBound()).isEqualTo(59896);
    }

    @Test
    @DisplayName("Completed search with no cover: proven infeasible")
    void provenInfeasible() {
        assertThat(pipeline(5_000_000).solve(TestData.case2(), 2).kind())
                .isEqualTo(SolverPipeline.Kind.PROVEN_INFEASIBLE);
    }

    @Test
    @DisplayName("Exhausted budget and no cover found: inconclusive, never proven infeasible")
    void inconclusive() {
        SolverPipeline.Outcome outcome = pipeline(1).solve(TestData.case2(), 2);

        assertThat(outcome.kind()).isEqualTo(SolverPipeline.Kind.INCONCLUSIVE);
        assertThat(outcome.solution()).isEmpty();
    }

    @Test
    @DisplayName("Exhausted budget: the heuristic answer never costs more than the exact search's incumbent")
    void heuristicNeverWorseThanIncumbent() {
        Random random = new Random(20_261_122L);
        int withIncumbent = 0;
        for (int i = 0; i < 200; i++) {
            Dataset data = RandomInstances.random(random, 2 + random.nextInt(8), 4 + random.nextInt(12),
                    i % 2 == 0 ? 0.5 : 0.9, i % 3 == 0 ? 3 : 200);
            int k = 1 + random.nextInt(data.transporters().size());

            SolverPipeline.Outcome outcome = pipeline(1 + random.nextInt(20)).solve(data, k);

            if (outcome.kind() == SolverPipeline.Kind.HEURISTIC) {
                assertThat(outcome.solution().orElseThrow().method()).isEqualTo("HEURISTIC");
                if (outcome.exactIncumbentCost().isPresent()) {
                    withIncumbent++;
                    assertThat(outcome.solution().get().totalCost()).as("case %d", i)
                            .isLessThanOrEqualTo(outcome.exactIncumbentCost().get());
                }
            }
            if (outcome.exactIncumbentCost().isPresent()) {
                assertThat(outcome.kind()).as("an incumbent is always returned, case %d", i)
                        .isIn(SolverPipeline.Kind.EXACT, SolverPipeline.Kind.HEURISTIC);
            }
        }
        assertThat(withIncumbent).isGreaterThan(50);
    }
}
