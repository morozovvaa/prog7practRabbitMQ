package com.weather.consumer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO для ответа с данными о погоде.
 * Отправляется в RabbitMQ очередь ответов после получения данных от Weather API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherResponse implements Serializable {
    
    /**
     * Идентификатор корреляции для связи с исходным запросом
     */
    private String correlationId;
    
    /**
     * Название города
     */
    private String city;
    
    /**
     * Температура в градусах Цельсия
     */
    private double temperature;
    
    /**
     * Описание погодных условий
     */
    private String description;
    
    /**
     * Влажность в процентах
     */
    private int humidity;
    
    /**
     * Скорость ветра в м/с
     */
    private double windSpeed;
    
    /**
     * Общее количество городов в запросе
     */
    private int totalCities;
    
    /**
     * Флаг успешности получения данных
     */
    private boolean success;
    
    /**
     * Сообщение об ошибке (если success = false)
     */
    private String errorMessage;
    
    /**
     * Временная метка получения данных
     */
    private LocalDateTime timestamp;
}
