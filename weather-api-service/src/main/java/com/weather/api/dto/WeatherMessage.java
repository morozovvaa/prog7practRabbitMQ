package com.weather.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Сообщение для отправки в RabbitMQ очередь запросов.
 * Содержит информацию о запрашиваемом городе и метаданные для корреляции.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherMessage implements Serializable {
    
    /**
     * Уникальный идентификатор корреляции для связи запроса и ответа
     */
    private String correlationId;
    
    /**
     * Название города для запроса погоды
     */
    private String city;
    
    /**
     * Общее количество городов в запросе (для агрегации)
     */
    private int totalCities;
    
    /**
     * Временная метка создания сообщения
     */
    private LocalDateTime timestamp;
}
