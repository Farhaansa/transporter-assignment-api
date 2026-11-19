package com.transporterassignment.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sends visitors of the bare service URL to the API documentation instead of a 404. */
@Hidden
@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Void> root() {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/swagger-ui.html").build();
    }
}
