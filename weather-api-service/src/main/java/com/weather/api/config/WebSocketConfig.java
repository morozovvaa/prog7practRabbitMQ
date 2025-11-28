package com.weather.api.config;

import com.weather.api.websocket.WeatherWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Конфигурация WebSocket для real-time коммуникации с фронтендом.
 * 
 * WebSocket обеспечивает постоянное двунаправленное соединение между клиентом и сервером,
 * позволяя серверу отправлять данные клиенту в реальном времени без необходимости опроса (polling).
 * 
 * Преимущества WebSocket по сравнению с HTTP polling:
 * 1. Низкая задержка (latency) - сообщения доставляются мгновенно
 * 2. Меньше нагрузки на сервер - нет постоянных HTTP запросов
 * 3. Эффективное использование ресурсов - одно постоянное соединение
 * 4. Лучший UX - пользователь видит обновления в реальном времени
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WeatherWebSocketHandler weatherWebSocketHandler;

    /**
     * Регистрирует WebSocket handler и endpoint.
     * 
     * Endpoint: ws://localhost:8080/ws/weather
     * 
     * setAllowedOrigins("*") разрешает подключения с любых доменов.
     * В production следует указать конкретные домены для безопасности.
     * 
     * @param registry реестр WebSocket обработчиков
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(weatherWebSocketHandler, "/ws/weather")
                .setAllowedOrigins("*"); // Разрешаем CORS для любых источников
    }
}