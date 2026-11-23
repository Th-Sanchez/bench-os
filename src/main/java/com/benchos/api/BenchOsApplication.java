package com.benchos.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BenchOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BenchOsApplication.class, args);
    }
}
