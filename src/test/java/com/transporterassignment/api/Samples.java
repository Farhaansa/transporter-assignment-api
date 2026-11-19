package com.transporterassignment.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads the request bodies in {@code samples/}, so the API tests also prove the published samples work. */
final class Samples {

    private Samples() {
    }

    static String read(String fileName) {
        try {
            return Files.readString(Path.of("samples", fileName));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String case1() {
        return read("case1-input.json");
    }

    static String case2() {
        return read("case2-input.json");
    }

    static String maxTransporters(Object k) {
        return "{\"maxTransporters\": " + k + "}";
    }
}
