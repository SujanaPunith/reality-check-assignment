package com.comeon.assignment.realitycheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RealityCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealityCheckApplication.class, args);
    }
}
