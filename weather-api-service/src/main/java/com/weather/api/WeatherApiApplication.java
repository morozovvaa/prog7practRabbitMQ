package com.weather.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс приложения Weather API Service.
 * Предоставляет REST API для приема запросов на получение прогнозов погоды
 * и координирует взаимодействие с RabbitMQ для асинхронной обработки.
 */
@SpringBootApplication
public class WeatherApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherApiApplication.class, args);
    }
}
