package com.transporterassignment.dto;

public record InputResponse(String status, String message) {

    public static InputResponse saved() {
        return new InputResponse("success", "Input data saved successfully.");
    }
}
