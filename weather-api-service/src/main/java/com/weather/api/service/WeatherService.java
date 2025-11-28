package com.weather.api.service;

import com.weather.api.dto.AggregatedWeatherReport;
import com.weather.api.dto.WeatherData;
import com.weather.api.dto.WeatherMessage;
import com.weather.api.dto.WeatherRequestDto;
import com.weather.api.websocket.WeatherWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.weather}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key.request}")
    private String requestRoutingKey;

    private final Map<String, CompletableFuture<AggregatedWeatherReport>> pendingRequests = 
            new ConcurrentHashMap<>();

    private final Map<String, WebSocketSession> webSocketSessions = new ConcurrentHashMap<>();
    private final Map<String, WeatherWebSocketHandler> webSocketHandlers = new ConcurrentHashMap<>();

    /**
     * СТАРЫЙ РЕЖИМ: HTTP Request-Reply
     */
    public AggregatedWeatherReport processWeatherRequest(WeatherRequestDto requestDto) throws Exception {
        String correlationId = UUID.randomUUID().toString();
        log.info("🔄 [HTTP MODE] Processing weather request with correlation ID: {}", correlationId);
        log.info("Cities requested: {}", requestDto.getCities());

        CompletableFuture<AggregatedWeatherReport> future = new CompletableFuture<>();
        pendingRequests.put(correlationId, future);

        int totalCities = requestDto.getCities().size();
        for (String city : requestDto.getCities()) {
            WeatherMessage message = new WeatherMessage(
                    correlationId,
                    city,
                    totalCities,
                    LocalDateTime.now()
            );

            log.debug("📤 Sending message for city: {} with correlation ID: {}", city, correlationId);
            rabbitTemplate.convertAndSend(exchangeName, requestRoutingKey, message);
        }

        try {
            AggregatedWeatherReport report = future.get(60, TimeUnit.SECONDS);
            log.info("✅ [HTTP MODE] Received aggregated report for correlation ID: {}", correlationId);
            return report;
        } catch (Exception e) {
            log.error("❌ [HTTP MODE] Error or timeout waiting for aggregated report: {}", e.getMessage());
            throw new Exception("Failed to get weather data: " + e.getMessage());
        } finally {
            pendingRequests.remove(correlationId);
        }
    }

    /**
     * НОВЫЙ РЕЖИМ: WebSocket Real-time
     */
    public void processWeatherRequestWebSocket(
            WeatherRequestDto requestDto,
            WebSocketSession session,
            WeatherWebSocketHandler handler) {
        
        String correlationId = UUID.randomUUID().toString();
        log.info("🌐 [WEBSOCKET MODE] Processing weather request with correlation ID: {}", correlationId);
        log.info("Cities requested: {}", requestDto.getCities());

        webSocketSessions.put(correlationId, session);
        webSocketHandlers.put(correlationId, handler);
        handler.registerSession(correlationId, session);

        try {
            handler.sendUpdate(correlationId, Map.of(
                "type", "PROCESSING_STARTED",
                "correlationId", correlationId,
                "totalCities", requestDto.getCities().size(),
                "cities", requestDto.getCities()
            ));

            int totalCities = requestDto.getCities().size();
            for (String city : requestDto.getCities()) {
                WeatherMessage message = new WeatherMessage(
                        correlationId,
                        city,
                        totalCities,
                        LocalDateTime.now()
                );

                log.debug("📤 Sending message for city: {} with correlation ID: {}", city, correlationId);
                rabbitTemplate.convertAndSend(exchangeName, requestRoutingKey, message);
            }

            log.info("✅ All {} messages sent to RabbitMQ for correlation ID: {}", 
                totalCities, correlationId);

        } catch (Exception e) {
            log.error("❌ Error processing WebSocket request: {}", e.getMessage(), e);
            handler.sendUpdate(correlationId, Map.of(
                "type", "ERROR",
                "message", "Error processing request: " + e.getMessage()
            ));
            cleanup(correlationId, handler);
        }
    }

    /**
     * КЛЮЧЕВОЙ МЕТОД: Получение индивидуальных результатов от Aggregator
     */
    @RabbitListener(queues = "weather.individual.response.queue")
    public void receiveIndividualWeatherResponse(WeatherData weatherData) {
        String correlationId = weatherData.getCorrelationId();
        log.info("📦 [WEBSOCKET] Received individual weather data for city: {} (correlation ID: {})",
                weatherData.getCity(), correlationId);

        WeatherWebSocketHandler handler = webSocketHandlers.get(correlationId);
        
        if (handler != null) {
            try {
                handler.sendUpdate(correlationId, Map.of(
                    "type", "INDIVIDUAL_RESULT",
                    "data", weatherData
                ));
                
                log.debug("✅ Sent individual result to WebSocket client for city: {}", 
                    weatherData.getCity());
            } catch (Exception e) {
                log.error("❌ Error sending individual update: {}", e.getMessage(), e);
            }
        } else {
            log.warn("⚠️ No WebSocket handler found for correlation ID: {}", correlationId);
        }
    }

    /**
     * Получение финального агрегированного отчета
     */
    @RabbitListener(queues = "${rabbitmq.queue.aggregated}")
    public void receiveAggregatedReport(AggregatedWeatherReport report) {
        String correlationId = report.getCorrelationId();
        log.info("📊 Received aggregated report for correlation ID: {}", correlationId);
        log.debug("Report details: {} cities, {} successful, {} failed, partial: {}", 
                report.getTotalCities(), report.getSuccessCount(), report.getFailureCount(),
                report.isPartial());

        CompletableFuture<AggregatedWeatherReport> httpFuture = pendingRequests.get(correlationId);
        WeatherWebSocketHandler wsHandler = webSocketHandlers.get(correlationId);

        if (httpFuture != null) {
            httpFuture.complete(report);
            log.debug("✅ [HTTP MODE] Completed future for correlation ID: {}", correlationId);
            
        } else if (wsHandler != null) {
            try {
                wsHandler.sendUpdate(correlationId, Map.of(
                    "type", "FINAL_REPORT",
                    "data", report
                ));
                
                log.info("✅ [WEBSOCKET MODE] Sent final report to client for correlation ID: {}", 
                    correlationId);
                
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        cleanup(correlationId, wsHandler);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                
            } catch (Exception e) {
                log.error("❌ Error sending final report: {}", e.getMessage(), e);
            }
        } else {
            log.warn("⚠️ No pending request found for correlation ID: {}", correlationId);
        }
    }

    private void cleanup(String correlationId, WeatherWebSocketHandler handler) {
        webSocketSessions.remove(correlationId);
        webSocketHandlers.remove(correlationId);
        handler.closeSession(correlationId);
        log.debug("🧹 Cleaned up resources for correlation ID: {}", correlationId);
    }
}