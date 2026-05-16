package com.forestry.aireviewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ForestryAiReviewerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForestryAiReviewerApplication.class, args);
    }
}
