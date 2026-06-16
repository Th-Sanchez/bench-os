package dev.peluci.benchos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class BenchOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BenchOsApplication.class, args);
    }

}
