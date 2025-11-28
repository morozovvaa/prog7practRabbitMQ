package com.weather.aggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO для ответа с данными о погоде от Consumer Service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherResponse implements Serializable {
    
    private String correlationId;
    private String city;
    private double temperature;
    private String description;
    private int humidity;
    private double windSpeed;
    private int totalCities;
    private boolean success;
    private String errorMessage;
    private LocalDateTime timestamp;
}
