package com.weather.aggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Агрегированный отчет о погоде для всех запрошенных городов. Добавлен флаг partial для частичных результатов.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedWeatherReport implements Serializable {
    /**
     * Идентификатор корреляции
     */
    private String correlationId;
    
    /**
     * Общее количество запрошенных городов
     */
    private int totalCities;
    
    /**
     * Количество успешно обработанных городов
     */
    private int successCount;
    
    /**
     * Количество неудачных запросов
     */
    private int failureCount;
    
    /**
     * Список данных о погоде
     */
    private List<WeatherData> reports;
    
    /**
     * Временная метка формирования отчета
     */
    private LocalDateTime timestamp;
    
    /**
     * НОВОЕ: Флаг частичного результата.
     * true - если отчет сформирован по таймауту (не все ответы получены)
     * false - если получены все ответы
     */
    private boolean partial;
    
    /**
     * НОВОЕ: Сообщение о причине частичного результата
     */
    private String partialReason;
}