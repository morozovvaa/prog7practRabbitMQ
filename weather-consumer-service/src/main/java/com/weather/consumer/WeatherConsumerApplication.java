package com.weather.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Главный класс приложения Weather Consumer Service.
 * Отвечает за получение сообщений из RabbitMQ, вызов внешнего Weather API
 * и отправку результатов в очередь ответов.
 */
@SpringBootApplication
public class WeatherConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherConsumerApplication.class, args);
    }
}
