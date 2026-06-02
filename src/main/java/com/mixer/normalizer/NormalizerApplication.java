package com.mixer.normalizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NormalizerApplication {
    public static void main(String[] args) {
        SpringApplication.run(NormalizerApplication.class, args);
    }
}
