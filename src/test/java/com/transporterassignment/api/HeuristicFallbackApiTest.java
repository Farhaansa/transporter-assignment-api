package com.transporterassignment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transporterassignment.TestData;
import com.transporterassignment.model.Dataset;
import com.transporterassignment.repository.DatasetRepository;
import com.transporterassignment.solver.RandomInstances;
import com.transporterassignment.solver.SolverPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With a 2-node exact budget the branch-and-bound search always stops early, so every answer comes from the
 * coverage-first local search and must be reported as HEURISTIC.
 */
@SpringBootTest(properties = "solver.max-search-nodes=2")
@AutoConfigureMockMvc
class HeuristicFallbackApiTest {

    private static final String INPUT = "/api/v1/transporters/input";
    private static final String ASSIGNMENT = "/api/v1/transporters/assignment";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DatasetRepository repository;

    @Autowired
    private SolverPipeline pipeline;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void resetState() {
        repository.clear();
    }

    private void saveInput(String body) throws Exception {
        mvc.perform(post(INPUT).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private ResultActions assign(int k) throws Exception {
        return mvc.perform(post(ASSIGNMENT).contentType(MediaType.APPLICATION_JSON).content(Samples.maxTransporters(k)));
    }

    @Test
    @DisplayName("Budget exhausted with a feasible incumbent: HEURISTIC, budget note with lower-bound gap, no 422")
    void budgetExhaustedReportsHeuristic() throws Exception {
        long incumbent = pipeline.solve(TestData.case1(), 3).exactIncumbentCost().orElseThrow();
        saveInput(Samples.case1());

        String body = assign(3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.solver").value("HEURISTIC"))
                .andExpect(jsonPath("$.assignments.length()").value(5))
                .andExpect(jsonPath("$.note").value(startsWith("The exact search over C(7, 3) = 35 candidate sets "
                        + "stopped at its budget of 2 search nodes (solver.max-search-nodes) before proving a minimum")))
                .andExpect(jsonPath("$.note").value(containsString("best answer found by local search")))
                .andExpect(jsonPath("$.note").value(containsString("It is not proven minimum.")))
                .andExpect(jsonPath("$.note").value(containsString("of the theoretical lower bound 59896")))
                .andExpect(jsonPath("$.note").value(containsString("does not measure that distance")))
                .andReturn().getResponse().getContentAsString();

        JsonNode response = mapper.readTree(body);
        assertThat(response.get("totalCost").asLong()).isLessThanOrEqualTo(incumbent);
        // 60139 is the proven optimum for k=3; the gap to the bound 59896 is 243, i.e. at most 0.5 %.
        assertThat(response.get("totalCost").asLong()).isEqualTo(60139);
        assertThat(response.get("note").asText()).contains("Total cost 60139 is within 0.5% of the theoretical "
                + "lower bound 59896");
    }

    @Test
    @DisplayName("Feasibility regression: greedy finds no cover, but one exists; local search answers HEURISTIC")
    void localSearchFindsCoverGreedyMisses() throws Exception {
        // Test Case 2 plus V, k=3: {X, Y, Z} covers all lanes for 7500, but greedy cover needs four transporters.
        saveInput(caseTwoWithExtraTransporter());

        assign(3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solver").value("HEURISTIC"))
                .andExpect(jsonPath("$.totalCost").value(7500))
                .andExpect(jsonPath("$.selectedTransporters").value(contains(1, 2, 3)));
    }

    @Test
    @DisplayName("No cover found: the labelled search-inconclusive outcome, not the proven-infeasible message")
    void inconclusiveIsNotInfeasible() throws Exception {
        // 4 lanes, 12 transporters that each quote a single lane, k=3: no cover exists, but proving that takes
        // more than 2 nodes, and greedy and local search find nothing. Only a completed search may say "can cover".
        saveInput(oneLanePerTransporter(4, 3));

        assign(3)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(startsWith("Search inconclusive:")))
                .andExpect(jsonPath("$.message").value(containsString("One may still exist.")))
                .andExpect(jsonPath("$.message").value(containsString("solver.heuristic-time-ms")))
                .andExpect(jsonPath("$.message").value(not(containsString("can cover"))));
    }

    @Test
    @DisplayName("Deterministic: the same input gives byte-identical heuristic responses")
    void deterministicResponses() throws Exception {
        String input = toJson(RandomInstances.plantedCover(new Random(42), 20, 200, 10, 0.15, 1000));

        saveInput(input);
        String first = assign(10).andExpect(status().isOk())
                .andExpect(jsonPath("$.solver").value("HEURISTIC"))
                .andReturn().getResponse().getContentAsString();
        String second = assign(10).andReturn().getResponse().getContentAsString();
        saveInput(input);
        String third = assign(10).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
    }

    /** {@code lanes} lanes; {@code perLane} transporters quote each lane, and each transporter quotes one lane. */
    private String oneLanePerTransporter(int lanes, int perLane) throws Exception {
        var root = mapper.createObjectNode();
        var laneArray = root.putArray("lanes");
        var transporters = root.putArray("transporters");
        for (int lane = 1; lane <= lanes; lane++) {
            laneArray.addObject().put("id", lane).put("origin", "O" + lane).put("destination", "D" + lane);
            for (int j = 0; j < perLane; j++) {
                int id = (lane - 1) * perLane + j + 1;
                transporters.addObject().put("id", id).put("name", "T" + id)
                        .putArray("laneQuotes").addObject().put("laneId", lane).put("quote", 10 + j);
            }
        }
        return mapper.writeValueAsString(root);
    }

    private static String caseTwoWithExtraTransporter() {
        return Samples.case2().replaceFirst("]\\s*}\\s*$",
                ", {\"id\": 5, \"name\": \"V\", \"laneQuotes\": [{\"laneId\": 5, \"quote\": 5000}]}]}");
    }

    private String toJson(Dataset data) throws Exception {
        var root = mapper.createObjectNode();
        var lanes = root.putArray("lanes");
        data.lanes().forEach(l -> lanes.addObject()
                .put("id", l.id()).put("origin", l.origin()).put("destination", l.destination()));
        var transporters = root.putArray("transporters");
        data.transporters().forEach(t -> {
            var node = transporters.addObject().put("id", t.id()).put("name", t.name());
            var quotes = node.putArray("laneQuotes");
            t.quotes().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(q -> quotes.addObject().put("laneId", q.getKey()).put("quote", q.getValue()));
        });
        return mapper.writeValueAsString(root);
    }
}
