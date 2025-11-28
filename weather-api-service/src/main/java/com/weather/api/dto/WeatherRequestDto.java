package com.weather.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для входящего запроса от фронтенда.
 * Содержит список городов, для которых необходимо получить прогноз погоды.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherRequestDto {
    
    /**
     * Список названий городов для запроса погоды
     */
    private List<String> cities;
}
