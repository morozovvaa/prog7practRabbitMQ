package com.weather.aggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherData {
    private String correlationId;
    private String city;
    private double temperature;
    private String description;
    private int humidity;
    private double windSpeed;
    private boolean success;
    private String errorMessage;
}