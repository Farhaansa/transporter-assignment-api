package com.transporterassignment.api;

import com.transporterassignment.repository.DatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
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
                .andExpect(jsonPath("$.assignments.length()").value(5));
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
