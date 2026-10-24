package org.viridexa.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ViridexaApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ViridexaApiApplication.class, args);
    }
}
