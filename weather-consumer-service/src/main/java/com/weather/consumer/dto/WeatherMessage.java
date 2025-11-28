package com.weather.consumer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO для сообщения запроса из RabbitMQ.
 * Содержит информацию о городе для запроса погоды.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherMessage implements Serializable {
    
    private String correlationId;
    private String city;
    private int totalCities;
    private LocalDateTime timestamp;
}
