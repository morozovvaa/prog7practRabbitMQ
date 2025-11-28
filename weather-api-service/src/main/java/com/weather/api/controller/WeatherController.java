package com.weather.api.controller;

import com.weather.api.dto.AggregatedWeatherReport;
import com.weather.api.dto.WeatherRequestDto;
import com.weather.api.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST контроллер для обработки HTTP запросов на получение прогнозов погоды.
 * Предоставляет API endpoint для фронтенда.
 */
@Slf4j
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Разрешаем CORS для фронтенда
public class WeatherController {

    private final WeatherService weatherService;

    /**
     * Endpoint для получения прогнозов погоды для списка городов.
     * 
     * HTTP метод: POST
     * URL: /api/weather/forecast
     * 
     * Пример запроса:
     * {
     *   "cities": ["Moscow", "London", "Paris", "New York", "Tokyo"]
     * }
     * 
     * Пример ответа:
     * {
     *   "correlationId": "550e8400-e29b-41d4-a716-446655440000",
     *   "totalCities": 5,
     *   "successCount": 5,
     *   "failureCount": 0,
     *   "reports": [
     *     {
     *       "city": "Moscow",
     *       "temperature": 15.5,
     *       "description": "Clear sky",
     *       "humidity": 65,
     *       "windSpeed": 3.5,
     *       "success": true
     *     },
     *     ...
     *   ],
     *   "timestamp": "2025-10-30T10:00:00"
     * }
     * 
     * @param requestDto DTO с списком городов
     * @return ResponseEntity с агрегированным отчетом или ошибкой
     */
    @PostMapping("/forecast")
    public ResponseEntity<?> getWeatherForecast(@RequestBody WeatherRequestDto requestDto) {
        log.info("Received weather forecast request for {} cities", requestDto.getCities().size());
        
        try {
            // Валидация входных данных
            if (requestDto.getCities() == null || requestDto.getCities().isEmpty()) {
                log.warn("Empty cities list in request");
                return ResponseEntity
                        .badRequest()
                        .body("Cities list cannot be empty");
            }

            // Обработка запроса через сервис
            AggregatedWeatherReport report = weatherService.processWeatherRequest(requestDto);
            
            log.info("Successfully processed weather request, returning {} reports", 
                    report.getReports().size());
            
            return ResponseEntity.ok(report);
            
        } catch (Exception e) {
            log.error("Error processing weather request: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing request: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint для проверки работоспособности сервиса.
     * 
     * @return Статус сервиса
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Weather API Service is running");
    }
}
