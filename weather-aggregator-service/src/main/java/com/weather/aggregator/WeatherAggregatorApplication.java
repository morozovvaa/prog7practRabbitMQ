package com.weather.aggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс приложения Weather Aggregator Service.
 * Реализует паттерн Aggregator из Enterprise Integration Patterns.
 * Собирает множество ответов от Weather Consumer и формирует единый агрегированный отчет.
 */
@SpringBootApplication
@EnableScheduling // Включаем поддержку планировщика для таймаутов
public class WeatherAggregatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherAggregatorApplication.class, args);
    }
}
