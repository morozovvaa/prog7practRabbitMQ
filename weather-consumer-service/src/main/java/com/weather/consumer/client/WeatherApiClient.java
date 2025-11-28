package com.weather.consumer.client;

import com.google.common.util.concurrent.RateLimiter;
import com.weather.consumer.dto.OpenWeatherMapResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Клиент для взаимодействия с OpenWeatherMap API.
 * Выполняет HTTP запросы для получения данных о погоде.
 * 
 * Возможности:
 * - Кэширование результатов (5 минут TTL)
 * - Rate limiting (1 запрос/секунду)
 * - Автоматическая обработка ошибок
 */
@Slf4j
@Component
public class WeatherApiClient {

    private final RestTemplate restTemplate;
    
    // Rate Limiter: максимум 1 запрос в секунду
    private final RateLimiter rateLimiter = RateLimiter.create(1.0);

    @Value("${weather.api.url}")
    private String apiUrl;

    @Value("${weather.api.key}")
    private String apiKey;

    /**
     * Конструктор для внедрения зависимостей.
     * RestTemplate внедряется через Spring IoC.
     */
    public WeatherApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        log.info("WeatherApiClient initialized with rate limit: 1 req/sec");
    }

    /**
     * Получает данные о погоде для указанного города.
     * 
     * Кэширование:
     * - value = "weather": имя кэша (должно совпадать с cache-names в yml)
     * - key = "#city": ключ кэша - название города
     * - unless = "#result == null": не кэшировать null результаты
     * 
     * Поведение:
     * 1. Проверяет кэш - если есть данные, возвращает их (без вызова API)
     * 2. Если данных нет - применяет rate limiting
     * 3. Делает HTTP запрос к OpenWeatherMap API
     * 4. Кэширует результат на 5 минут
     * 
     * @param city Название города
     * @return Данные о погоде от OpenWeatherMap API
     * @throws Exception если произошла ошибка при запросе
     */
    @Cacheable(value = "weather", key = "#city", sync = true)
    public OpenWeatherMapResponse getWeatherForCity(String city) throws Exception {
        // Rate limiting: ждём разрешения перед запросом
        rateLimiter.acquire();
        
        log.info("🌐 CACHE MISS - Fetching weather data from API for city: {}", city);

        try {
            // Построение URL с параметрами
            String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
                    .queryParam("q", city)
                    .queryParam("appid", apiKey)
                    .queryParam("units", "metric") // Температура в Цельсиях
                    .toUriString();

            log.debug("API URL: {}", url.replace(apiKey, "***")); // Скрываем API ключ

            // Выполнение HTTP GET запроса
            OpenWeatherMapResponse response = restTemplate.getForObject(url, OpenWeatherMapResponse.class);

            if (response != null) {
                log.info("✅ Successfully fetched and CACHED weather for city: {}", city);
                log.debug("Temperature: {}°C, Humidity: {}%, Wind: {} m/s",
                        response.getMain().getTemp(),
                        response.getMain().getHumidity(),
                        response.getWind().getSpeed());
            } else {
                log.warn("⚠️ Received null response for city: {}", city);
            }

            return response;

        } catch (Exception e) {
            log.error("❌ Error fetching weather data for city {}: {}", city, e.getMessage());
            throw new Exception("Failed to fetch weather data for " + city + ": " + e.getMessage());
        }
    }
}