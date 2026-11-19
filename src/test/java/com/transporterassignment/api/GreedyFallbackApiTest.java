package com.transporterassignment.api;

import com.transporterassignment.repository.DatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With a tiny combination limit, C(7, 3) = 35 is too many and the greedy fallback must be reported. */
@SpringBootTest(properties = "solver.max-combinations=5")
@AutoConfigureMockMvc
class GreedyFallbackApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DatasetRepository repository;

    @BeforeEach
    void resetState() {
        repository.clear();
    }

    @Test
    void greedyPathIsFlagged() throws Exception {
        mvc.perform(post("/api/v1/transporters/input").contentType(MediaType.APPLICATION_JSON).content(Samples.case1()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/transporters/assignment").contentType(MediaType.APPLICATION_JSON)
                        .content(Samples.maxTransporters(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solver").value("GREEDY"))
                .andExpect(jsonPath("$.note").value(containsString("35")))
                .andExpect(jsonPath("$.note").value(containsString("may not be optimal")))
                .andExpect(jsonPath("$.note").value(containsString("may be higher than the minimum")))
                .andExpect(jsonPath("$.assignments.length()").value(5));
    }

    @Test
    @DisplayName("GREEDY with an equal-cost extra transporter: it is used and the cost is unchanged")
    void greedyBalanceUsesEqualCostTransporter() throws Exception {
        // N=4, k=2: C(4, 2) = 6 > 5, so the greedy fallback runs.
        String input = """
                {"lanes": [{"id": 1, "origin": "A", "destination": "B"}, {"id": 2, "origin": "C", "destination": "D"}],
                 "transporters": [
                   {"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": 10}, {"laneId": 2, "quote": 10}]},
                   {"id": 2, "name": "T2", "laneQuotes": [{"laneId": 2, "quote": 10}]},
                   {"id": 3, "name": "T3", "laneQuotes": [{"laneId": 1, "quote": 50}]},
                   {"id": 4, "name": "T4", "laneQuotes": [{"laneId": 1, "quote": 50}]}]}
                """;
        mvc.perform(post("/api/v1/transporters/input").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/transporters/assignment").contentType(MediaType.APPLICATION_JSON)
                        .content(Samples.maxTransporters(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solver").value("GREEDY"))
                .andExpect(jsonPath("$.totalCost").value(20))
                .andExpect(jsonPath("$.selectedTransporters").value(contains(1, 2)));
    }

    @Test
    @DisplayName("GREEDY 422 says a cover may still exist, never that none exists")
    void greedyInfeasibilityIsNotClaimedAsProof() throws Exception {
        // Test Case 2 plus T5, k=3: C(5, 3) = 10 > 5, so greedy runs. A cover exists ({X, Y, Z} = 7500),
        // but greedy picks Z, X, then W and needs a fourth transporter, so it gives up.
        String input = Samples.case2().replaceFirst("]\\s*}\\s*$",
                ", {\"id\": 5, \"name\": \"V\", \"laneQuotes\": [{\"laneId\": 5, \"quote\": 5000}]}]}");
        mvc.perform(post("/api/v1/transporters/input").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/transporters/assignment").contentType(MediaType.APPLICATION_JSON)
                        .content(Samples.maxTransporters(3)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("greedy heuristic")))
                .andExpect(jsonPath("$.message").value(containsString("one may still exist")))
                .andExpect(jsonPath("$.message").value(not(containsString("can cover"))));
    }

    @Test
    void smallSearchSpaceStaysExact() throws Exception {
        mvc.perform(post("/api/v1/transporters/input").contentType(MediaType.APPLICATION_JSON).content(Samples.case1()))
                .andExpect(status().isOk());

        // C(7, 7) = 1 combination, within the limit of 5.
        mvc.perform(post("/api/v1/transporters/assignment").contentType(MediaType.APPLICATION_JSON)
                        .content(Samples.maxTransporters(7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solver").value("EXACT"))
                .andExpect(jsonPath("$.totalCost").value(59896));
    }
}
