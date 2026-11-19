package com.transporterassignment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The Swagger examples must stay identical to the request bodies in samples/. */
class ApiExamplesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void examplesMatchSampleFiles() throws Exception {
        assertThat(mapper.readTree(ApiExamples.CASE_1))
                .isEqualTo(mapper.readTree(Files.readString(Path.of("samples", "case1-input.json"))));
        assertThat(mapper.readTree(ApiExamples.CASE_2))
                .isEqualTo(mapper.readTree(Files.readString(Path.of("samples", "case2-input.json"))));
    }
}
