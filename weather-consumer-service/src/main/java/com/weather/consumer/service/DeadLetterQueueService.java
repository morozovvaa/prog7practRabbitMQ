package com.weather.consumer.service;

import com.weather.consumer.dto.WeatherMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Сервис для обработки сообщений из Dead Letter Queue.
 * Логирует проблемные сообщения для дальнейшего анализа.
 */
@Slf4j
@Service
public class DeadLetterQueueService {

    @RabbitListener(queues = "weather.request.dlq")
    public void handleDeadLetter(WeatherMessage weatherMessage) {
        log.error("=== DEAD LETTER MESSAGE RECEIVED ===");
        log.error("Correlation ID: {}", weatherMessage.getCorrelationId());
        log.error("City: {}", weatherMessage.getCity());
        log.error("Total Cities: {}", weatherMessage.getTotalCities());
        log.error("Timestamp: {}", weatherMessage.getTimestamp());
        log.error("====================================");
        
        // Здесь можно:
        // 1. Сохранить в базу данных для анализа
        // 2. Отправить уведомление администратору
        // 3. Попытаться обработать с другой логикой
        // 4. Отправить в систему мониторинга (Prometheus, ELK)
    }
}
