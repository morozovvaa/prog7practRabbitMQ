package com.weather.aggregator.service;

import com.weather.aggregator.dto.AggregatedWeatherReport;
import com.weather.aggregator.dto.WeatherData;
import com.weather.aggregator.dto.WeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherAggregatorService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.weather}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key.aggregated}")
    private String aggregatedRoutingKey;

    @Value("${aggregator.timeout}")
    private long timeoutSeconds;

    private static final String INDIVIDUAL_RESPONSE_ROUTING_KEY = "weather.individual.response";

    private final Map<String, AggregationContext> aggregationStore = new ConcurrentHashMap<>();

    private static class AggregationContext {
        String correlationId;
        int totalCities;
        int receivedCount;
        List<WeatherData> weatherDataList;
        LocalDateTime startTime;
        int successCount;
        int failureCount;

        AggregationContext(String correlationId, int totalCities) {
            this.correlationId = correlationId;
            this.totalCities = totalCities;
            this.receivedCount = 0;
            this.weatherDataList = new ArrayList<>();
            this.startTime = LocalDateTime.now();
            this.successCount = 0;
            this.failureCount = 0;
        }

        WeatherData addResponse(WeatherResponse response) {
            WeatherData data = new WeatherData();
            data.setCorrelationId(correlationId);
            data.setCity(response.getCity());
            data.setTemperature(response.getTemperature());
            data.setDescription(response.getDescription());
            data.setHumidity(response.getHumidity());
            data.setWindSpeed(response.getWindSpeed());
            data.setSuccess(response.isSuccess());
            data.setErrorMessage(response.getErrorMessage());

            weatherDataList.add(data);
            receivedCount++;

            if (response.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
            
            return data;
        }

        boolean isComplete() {
            return receivedCount >= totalCities;
        }

        AggregatedWeatherReport buildReport(boolean isPartial, String partialReason) {
            AggregatedWeatherReport report = new AggregatedWeatherReport();
            report.setCorrelationId(correlationId);
            report.setTotalCities(totalCities);
            report.setReports(weatherDataList);
            report.setTimestamp(LocalDateTime.now());
            report.setSuccessCount(successCount);
            report.setFailureCount(failureCount);
            report.setPartial(isPartial);
            report.setPartialReason(partialReason);
            return report;
        }
        
        AggregatedWeatherReport buildReport() {
            return buildReport(false, null);
        }
    }

    /**
     * КЛЮЧЕВОЙ МЕТОД: Получает ответы и отправляет индивидуальные результаты
     */
    @RabbitListener(queues = "${rabbitmq.queue.response}")
    public void aggregateWeatherResponse(WeatherResponse response) {
        log.info("📨 Received weather response for city: {} (correlation ID: {})",
                response.getCity(), response.getCorrelationId());

        String correlationId = response.getCorrelationId();

        AggregationContext context = aggregationStore.computeIfAbsent(
                correlationId,
                id -> {
                    log.info("🆕 Creating new aggregation context for correlation ID: {}", id);
                    return new AggregationContext(id, response.getTotalCities());
                }
        );

        synchronized (context) {
            WeatherData individualData = context.addResponse(response);

            log.debug("📊 Aggregation progress for {}: {}/{} responses received",
                    correlationId, context.receivedCount, context.totalCities);

            // ОТПРАВЛЯЕМ ИНДИВИДУАЛЬНЫЙ РЕЗУЛЬТАТ СРАЗУ
            try {
                rabbitTemplate.convertAndSend(
                    exchangeName, 
                    INDIVIDUAL_RESPONSE_ROUTING_KEY, 
                    individualData
                );
                
                log.info("📤 Forwarded individual result for city: {} to individual queue", 
                    individualData.getCity());
                
            } catch (Exception e) {
                log.error("❌ Error sending individual result: {}", e.getMessage(), e);
            }

            // Проверяем завершенность агрегации
            if (context.isComplete()) {
                log.info("✅ All responses received for correlation ID: {}. Building complete report.",
                        correlationId);

                AggregatedWeatherReport report = context.buildReport(false, null);

                log.info("📊 Aggregated report ready: {} total, {} successful, {} failed",
                        report.getTotalCities(), report.getSuccessCount(), report.getFailureCount());

                rabbitTemplate.convertAndSend(exchangeName, aggregatedRoutingKey, report);

                log.info("📤 Complete aggregated report sent for correlation ID: {}", correlationId);

                aggregationStore.remove(correlationId);
                log.debug("🧹 Aggregation context removed for correlation ID: {}", correlationId);
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void cleanupExpiredAggregations() {
        LocalDateTime now = LocalDateTime.now();
        List<String> expiredIds = new ArrayList<>();

        aggregationStore.forEach((correlationId, context) -> {
            long secondsElapsed = Duration.between(context.startTime, now).getSeconds();
            
            if (secondsElapsed > timeoutSeconds) {
                log.warn("⏱️ Aggregation timeout for correlation ID: {} (elapsed: {}s, timeout: {}s)",
                        correlationId, secondsElapsed, timeoutSeconds);
                expiredIds.add(correlationId);
            }
        });

        expiredIds.forEach(correlationId -> {
            AggregationContext context = aggregationStore.remove(correlationId);
            
            if (context != null) {
                synchronized (context) {
                    int missingResponses = context.totalCities - context.receivedCount;
                    
                    log.warn("⚠️ Sending PARTIAL report for correlation ID: {}", correlationId);
                    log.warn("   Received: {}/{} responses", context.receivedCount, context.totalCities);
                    log.warn("   Missing: {} responses", missingResponses);
                    
                    String partialReason = String.format(
                        "Timeout after %ds: received only %d/%d responses. %d responses missing.",
                        timeoutSeconds,
                        context.receivedCount,
                        context.totalCities,
                        missingResponses
                    );
                    
                    AggregatedWeatherReport partialReport = context.buildReport(true, partialReason);
                    
                    try {
                        rabbitTemplate.convertAndSend(exchangeName, aggregatedRoutingKey, partialReport);
                        
                        log.info("📤 Partial report sent for correlation ID: {}", correlationId);
                        log.info("   Status: {} successful, {} failed, {} missing",
                                context.successCount,
                                context.failureCount,
                                missingResponses);
                        
                    } catch (Exception e) {
                        log.error("❌ Failed to send partial report for {}: {}", 
                                correlationId, e.getMessage());
                    }
                }
            }
        });
        
        if (!expiredIds.isEmpty()) {
            log.info("🧹 Cleaned up {} expired aggregation context(s)", expiredIds.size());
        }
    }
}