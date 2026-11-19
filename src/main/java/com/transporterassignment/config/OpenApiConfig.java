package com.transporterassignment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transporterAssignmentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Transporter Assignment API")
                .version("1.0.0")
                .description("""
                        Assigns every shipping lane to one transporter so the total cost is minimal, \
                        using at most maxTransporters transporters. \
                        Call POST /input first (try the "Test Case 1" example), then POST /assignment. \
                        Data is kept in memory and is lost when the service restarts."""));
    }
}
