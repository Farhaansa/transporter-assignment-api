package com.transporterassignment.api;

import com.transporterassignment.service.AssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Any unexpected exception becomes a generic 500 without internal details. */
@SpringBootTest
@AutoConfigureMockMvc
class UnexpectedErrorApiTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AssignmentService assignmentService;

    @Test
    void unexpectedExceptionIsHidden() throws Exception {
        when(assignmentService.assign(anyInt())).thenThrow(new IllegalStateException("internal detail 42"));

        mvc.perform(post("/api/v1/transporters/assignment").contentType(MediaType.APPLICATION_JSON)
                        .content(Samples.maxTransporters(3)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("internal detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
