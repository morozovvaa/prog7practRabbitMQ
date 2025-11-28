package com.weather.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO для ответа от OpenWeatherMap API.
 * Содержит только необходимые поля из полного ответа API.
 * 
 * Пример ответа API:
 * {
 *   "coord": {"lon": 37.6156, "lat": 55.7522},
 *   "weather": [{"id": 800, "main": "Clear", "description": "clear sky", "icon": "01d"}],
 *   "main": {
 *     "temp": 15.5,
 *     "feels_like": 14.2,
 *     "temp_min": 14.0,
 *     "temp_max": 17.0,
 *     "pressure": 1013,
 *     "humidity": 65
 *   },
 *   "wind": {"speed": 3.5, "deg": 180},
 *   "name": "Moscow"
 * }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenWeatherMapResponse {
    
    /**
     * Название города
     */
    private String name;
    
    /**
     * Основные погодные параметры
     */
    private Main main;
    
    /**
     * Описание погодных условий
     */
    private List<Weather> weather;
    
    /**
     * Информация о ветре
     */
    private Wind wind;
    
    /**
     * Вложенный класс для основных параметров
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Main {
        /**
         * Температура в Кельвинах (будет конвертирована в Цельсий)
         */
        private double temp;
        
        /**
         * Влажность в процентах
         */
        private int humidity;
    }
    
    /**
     * Вложенный класс для описания погоды
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Weather {
        /**
         * Краткое описание (например, "Clear", "Clouds")
         */
        private String main;
        
        /**
         * Детальное описание (например, "clear sky", "few clouds")
         */
        private String description;
    }
    
    /**
     * Вложенный класс для информации о ветре
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Wind {
        /**
         * Скорость ветра в м/с
         */
        private double speed;
    }
}
