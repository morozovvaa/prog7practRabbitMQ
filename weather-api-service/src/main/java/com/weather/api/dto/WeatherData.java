package com.weather.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherData {
    private String correlationId; 
    private String city;
    private Double temperature;
    private String description;
    private Integer humidity;
    private Double windSpeed;
    private boolean success;
    private String errorMessage;
}