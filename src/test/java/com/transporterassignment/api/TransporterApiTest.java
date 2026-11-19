package com.transporterassignment.api;

import com.transporterassignment.repository.DatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransporterApiTest {

    private static final String INPUT = "/api/v1/transporters/input";
    private static final String ASSIGNMENT = "/api/v1/transporters/assignment";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DatasetRepository repository;

    @BeforeEach
    void resetState() {
        repository.clear();
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static void assertErrorShape(ResultActions result, int code, String reason) throws Exception {
        result.andExpect(status().is(code))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.error").value(reason))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Nested
    @DisplayName("Happy paths")
    class HappyPaths {

        @Test
        void inputIsSaved() throws Exception {
            postJson(INPUT, Samples.case1())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Input data saved successfully."));
        }

        @Test
        @DisplayName("Test Case 1, k=3: 60139 with [1, 4, 5], solved exactly")
        void case1WithThree() throws Exception {
            postJson(INPUT, Samples.case1()).andExpect(status().isOk());

            postJson(ASSIGNMENT, Samples.read("assignment-k3.json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.totalCost").value(60139))
                    .andExpect(jsonPath("$.selectedTransporters").value(org.hamcrest.Matchers.contains(1, 4, 5)))
                    .andExpect(jsonPath("$.assignments", hasSize(5)))
                    .andExpect(jsonPath("$.assignments[0].laneId").value(1))
                    .andExpect(jsonPath("$.assignments[0].transporterId").value(5))
                    .andExpect(jsonPath("$.assignments[1].transporterId").value(1))
                    .andExpect(jsonPath("$.assignments[2].transporterId").value(4))
                    .andExpect(jsonPath("$.assignments[3].transporterId").value(4))
                    .andExpect(jsonPath("$.assignments[4].laneId").value(5))
                    .andExpect(jsonPath("$.assignments[4].transporterId").value(4))
                    .andExpect(jsonPath("$.solver").value("EXACT"))
                    .andExpect(jsonPath("$.note").doesNotExist());
        }

        @Test
        @DisplayName("Test Case 2 over HTTP: k=3 costs 7500, k=4 costs 7450")
        void case2() throws Exception {
            postJson(INPUT, Samples.case2()).andExpect(status().isOk());

            postJson(ASSIGNMENT, Samples.maxTransporters(3))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").value(7500))
                    .andExpect(jsonPath("$.selectedTransporters").value(org.hamcrest.Matchers.contains(1, 2, 3)));
            postJson(ASSIGNMENT, Samples.maxTransporters(4))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").value(7450))
                    .andExpect(jsonPath("$.assignments[3].transporterId").value(4));
        }

        @Test
        @DisplayName("maxTransporters above N is clamped to N and explained in a note")
        void kGreaterThanN() throws Exception {
            postJson(INPUT, Samples.case1()).andExpect(status().isOk());

            postJson(ASSIGNMENT, Samples.maxTransporters(10))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").value(59896))
                    .andExpect(jsonPath("$.solver").value("EXACT"))
                    .andExpect(jsonPath("$.note").value(containsString("maxTransporters (10)")))
                    .andExpect(jsonPath("$.note").value(containsString("7")));
        }

        @Test
        @DisplayName("A new /input replaces the previous data")
        void newInputReplacesOld() throws Exception {
            postJson(INPUT, Samples.case1()).andExpect(status().isOk());
            postJson(INPUT, Samples.case2()).andExpect(status().isOk());

            postJson(ASSIGNMENT, Samples.maxTransporters(3))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").value(7500));
        }

        @Test
        @DisplayName("A rejected /input keeps the previous data")
        void rejectedInputKeepsOld() throws Exception {
            postJson(INPUT, Samples.case1()).andExpect(status().isOk());
            postJson(INPUT, "{\"lanes\": [], \"transporters\": []}").andExpect(status().isBadRequest());

            postJson(ASSIGNMENT, Samples.maxTransporters(3))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").value(60139));
        }

        @Test
        void rootRedirectsToSwaggerUi() throws Exception {
            mvc.perform(get("/"))
                    .andExpect(status().isFound())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .header().string("Location", "/swagger-ui.html"));
        }

        @Test
        void openApiDocsListTheEndpoints() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/transporters/input']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/transporters/assignment']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/transporters/assignment'].post.description")
                            .value(containsString("our documented interpretation of the brief")))
                    .andExpect(jsonPath("$.components.schemas.AssignmentResponse.properties.solver.description")
                            .value(containsString("proven minimum")))
                    .andExpect(jsonPath("$.components.schemas.AssignmentResponse.properties.solver.description")
                            .value(containsString("may be higher than the minimum")));
        }
    }

    @Nested
    @DisplayName("400 Bad Request")
    class BadRequest {

        @Test
        void malformedJson() throws Exception {
            assertErrorShape(postJson(INPUT, "{\"lanes\": [ {\"id\": 1, "), 400, "Bad Request");
        }

        @Test
        void missingRequiredFields() throws Exception {
            String body = """
                    {"lanes": [{"id": 1, "origin": "A"}],
                     "transporters": [{"id": 1, "laneQuotes": [{"laneId": 1, "quote": 5}]}]}
                    """;
            ResultActions result = postJson(INPUT, body);
            assertErrorShape(result, 400, "Bad Request");
            result.andExpect(jsonPath("$.details", hasItem(containsString("lanes[0].destination"))))
                    .andExpect(jsonPath("$.details", hasItem(containsString("transporters[0].name"))));
        }

        @Test
        void missingLists() throws Exception {
            ResultActions result = postJson(INPUT, "{}");
            assertErrorShape(result, 400, "Bad Request");
            result.andExpect(jsonPath("$.details", hasItem(containsString("lanes"))))
                    .andExpect(jsonPath("$.details", hasItem(containsString("transporters"))));
        }

        @Test
        void emptyLists() throws Exception {
            assertErrorShape(postJson(INPUT, "{\"lanes\": [], \"transporters\": []}"), 400, "Bad Request");
        }

        @Test
        void nonPositiveQuote() throws Exception {
            String body = """
                    {"lanes": [{"id": 1, "origin": "A", "destination": "B"}],
                     "transporters": [{"id": 1, "name": "T", "laneQuotes": [{"laneId": 1, "quote": 0}]},
                                      {"id": 2, "name": "U", "laneQuotes": [{"laneId": 1, "quote": -4}]}]}
                    """;
            ResultActions result = postJson(INPUT, body);
            assertErrorShape(result, 400, "Bad Request");
            result.andExpect(jsonPath("$.details", hasItem(containsString("transporters[0].laneQuotes[0].quote"))))
                    .andExpect(jsonPath("$.details", hasItem(containsString("transporters[1].laneQuotes[0].quote"))));
        }

        @Test
        void fractionalQuote() throws Exception {
            String body = """
                    {"lanes": [{"id": 1, "origin": "A", "destination": "B"}],
                     "transporters": [{"id": 1, "name": "T", "laneQuotes": [{"laneId": 1, "quote": 1.5}]}]}
                    """;
            assertErrorShape(postJson(INPUT, body), 400, "Bad Request");
        }

        @Test
        void duplicateLaneId() throws Exception {
            String body = """
                    {"lanes": [{"id": 1, "origin": "A", "destination": "B"}, {"id": 1, "origin": "C", "destination": "D"}],
                     "transporters": [{"id": 1, "name": "T", "laneQuotes": [{"laneId": 1, "quote": 5}]}]}
                    """;
            ResultActions result = postJson(INPUT, body);
            assertErrorShape(result, 400, "Bad Request");
            result.andExpect(jsonPath("$.details", hasItem("Duplicate lane id 1")));
        }

        @Test
        void duplicateTransporterId() throws Exception {
            String body = """
                    {"lanes": [{"id": 1, "origin": "A", "destination": "B"}],
                     "transporters": [{"id": 7, "name": "T", "laneQuotes": [{"laneId": 1, "quote": 5}]},
                                      {"id": 7, "name": "U", "laneQuotes": [{"laneId": 1, "quote": 6}]}]}
                    """;
            ResultActions result = postJson(INPUT, body);
            assertErrorShape(result, 400, "Bad Request");
            result.andExpect(jsonPath("$.details", hasItem("Duplicate transporter id 7")));
        }

        @Test
        void quoteForUnknownLane() throws Exception {
            String body = """
                    {"lanes": [{"id": 1, "origin": "A", "destination": "B"}],
                     "transporters": [{"id": 1, "name": "T", "laneQuotes": [{"laneId": 1, "quote": 5}, {"laneId": 9, "quote": 5}]}]}
                    """;
            ResultActions result = postJson(INPUT, body);
            assertErrorShape(result, 400, "Bad Request");
            result.andExpect(jsonPath("$.details", hasItem("Transporter 1 quotes unknown lane id 9")));
        }

        @Test
        void sameLaneQuotedTwiceByOneTransporter() throws Exception {
            String body = """
                    {"lanes": [{"id": 1, "origin": "A", "destination": "B"}],
                     "transporters": [{"id": 1, "name": "T", "laneQuotes": [{"laneId": 1, "quote": 5}, {"laneId": 1, "quote": 4}]}]}
                    """;
            ResultActions result = postJson(INPUT, body);
            assertErrorShape(result, 400, "Bad Request");
            result.andExpect(jsonPath("$.details", hasItem("Transporter 1 quotes lane 1 more than once")));
        }

        @Test
        void maxTransportersMissing() throws Exception {
            postJson(INPUT, Samples.case1()).andExpect(status().isOk());
            assertErrorShape(postJson(ASSIGNMENT, "{}"), 400, "Bad Request");
        }

        @Test
        void maxTransportersZero() throws Exception {
            postJson(INPUT, Samples.case1()).andExpect(status().isOk());
            assertErrorShape(postJson(ASSIGNMENT, Samples.maxTransporters(0)), 400, "Bad Request");
        }

        @Test
        void maxTransportersNegative() throws Exception {
            postJson(INPUT, Samples.case1()).andExpect(status().isOk());
            assertErrorShape(postJson(ASSIGNMENT, Samples.maxTransporters(-2)), 400, "Bad Request");
        }
    }

    @Nested
    @DisplayName("409, 422 and other errors")
    class OtherErrors {

        @Test
        @DisplayName("422 at /input when a lane has no quote, listing the lane ids")
        void unquotedLane() throws Exception {
            String body = """
                    {"lanes": [{"id": 1, "origin": "A", "destination": "B"},
                               {"id": 2, "origin": "C", "destination": "D"},
                               {"id": 3, "origin": "E", "destination": "F"}],
                     "transporters": [{"id": 1, "name": "T", "laneQuotes": [{"laneId": 2, "quote": 5}]}]}
                    """;
            ResultActions result = postJson(INPUT, body);
            assertErrorShape(result, 422, "Unprocessable Entity");
            result.andExpect(jsonPath("$.message").value(containsString("[1, 3]")));
        }

        @Test
        @DisplayName("422 when no k transporters can cover every lane")
        void infeasibleK() throws Exception {
            postJson(INPUT, Samples.case2()).andExpect(status().isOk());

            ResultActions result = postJson(ASSIGNMENT, Samples.maxTransporters(2));
            assertErrorShape(result, 422, "Unprocessable Entity");
            result.andExpect(jsonPath("$.message").value(containsString("maxTransporters")))
                    .andExpect(jsonPath("$.message").value(containsString("can cover")))
                    .andExpect(jsonPath("$.message").value(not(containsString("may still exist"))));
        }

        @Test
        @DisplayName("409 when /assignment is called before /input")
        void assignmentBeforeInput() throws Exception {
            assertErrorShape(postJson(ASSIGNMENT, Samples.maxTransporters(3)), 409, "Conflict");
        }

        @Test
        void unsupportedMediaType() throws Exception {
            ResultActions result = mvc.perform(post(INPUT).contentType(MediaType.TEXT_PLAIN).content("hello"));
            assertErrorShape(result, 415, "Unsupported Media Type");
        }

        @Test
        void wrongMethod() throws Exception {
            assertErrorShape(mvc.perform(get(INPUT)), 405, "Method Not Allowed");
        }

        @Test
        void unknownPath() throws Exception {
            ResultActions result = mvc.perform(get("/api/v1/nothing-here"));
            assertErrorShape(result, 404, "Not Found");
            result.andExpect(jsonPath("$.message").value(not(containsString("Exception"))));
        }
    }
}
