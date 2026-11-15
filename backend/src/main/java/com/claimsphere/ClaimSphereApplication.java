package com.claimsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClaimSphereApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimSphereApplication.class, args);
    }
}
