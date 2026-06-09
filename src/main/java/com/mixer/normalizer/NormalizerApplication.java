package com.mixer.normalizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
 * Точка входа приложения.
 * Spring Boot поднимает веб-сервер, читает application.yml и создает все
 * компоненты с аннотациями @Component/@Service/@RestController.
 */
@SpringBootApplication
// Включает фоновые задачи с @Scheduled, например периодический опрос оборудования.
@EnableScheduling
public class NormalizerApplication {
    public static void main(String[] args) {
        // Запускает весь Spring-контейнер и HTTP-сервер.
        SpringApplication.run(NormalizerApplication.class, args);
    }
}
