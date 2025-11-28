package com.weather.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weather.api.dto.WeatherRequestDto;
import com.weather.api.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик WebSocket соединений для real-time обновлений погоды.
 *  * Этот компонент управляет жизненным циклом WebSocket соединений:
 * - Открытие соединения (afterConnectionEstablished)
 * - Получение сообщений от клиента (handleTextMessage)
 * - Закрытие соединения (afterConnectionClosed)
 *  * Паттерн: Publish-Subscribe
 * Сервер публикует обновления всем подписанным клиентам через WebSocket.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherWebSocketHandler extends TextWebSocketHandler {

    private final WeatherService weatherService;
    private final ObjectMapper objectMapper;

    /**
     * Хранилище активных WebSocket сессий.
     * Key: correlationId (UUID запроса)
     * Value: WebSocketSession (соединение с клиентом)
     *      * ConcurrentHashMap обеспечивает потокобезопасность при параллельных операциях.
     */
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Вызывается при установке WebSocket соединения.
     *      * @param session WebSocket сессия клиента
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("🔗 WebSocket connection established: {}", session.getId());
 
        try {
             // Отправляем клиенту подтверждение подключения
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of(
                    "type", "CONNECTION_ESTABLISHED",
                    "message", "WebSocket connection successful",
                    "sessionId", session.getId()
                )
            )));
        } catch (Exception e) {
            log.error("Error sending connection confirmation: {}", e.getMessage());
        }
    }

    /**
     * Вызывается при получении текстового сообщения от клиента.
     *      * Ожидаемый формат сообщения:
     * {
     *   "cities": ["London", "Paris", "Moscow"]
     * }
     *      * После получения запроса:
     * 1. Парсит список городов
     * 2. Регистрирует WebSocket сессию для получения обновлений
     * 3. Инициирует асинхронную обработку через WeatherService
     * 4. WeatherService будет отправлять инкрементальные обновления через sendUpdate()
     *      * @param session WebSocket сессия
     * @param message Текстовое сообщение от клиента
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            log.info("📨 Received message from client {}: {}", session.getId(), message.getPayload());

            // Парсим запрос
            WeatherRequestDto request = objectMapper.readValue(
                message.getPayload(), 
                WeatherRequestDto.class
            );

            // Валидация
            if (request.getCities() == null || request.getCities().isEmpty()) {
                sendError(session, "Cities list cannot be empty");
                return;
            }

            log.info("Processing weather request for {} cities via WebSocket", request.getCities().size());

            // Инициируем асинхронную обработку с WebSocket callback
            weatherService.processWeatherRequestWebSocket(request, session, this);

        } catch (Exception e) {
            log.error("Error handling WebSocket message: {}", e.getMessage(), e);
            sendError(session, "Error processing request: " + e.getMessage());
        }
    }

    /**
     * Вызывается при закрытии WebSocket соединения.
     *      * @param session WebSocket сессия
     * @param status Статус закрытия
    */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("🔌 WebSocket connection closed: {} with status: {}", session.getId(), status);

        // Улучшенное удаление: удаляем все записи, связанные с этой закрытой сессией,
        // чтобы предотвратить утечки памяти и попытки отправки данных в закрытое соединение.
        activeSessions.values().removeIf(s -> s.getId().equals(session.getId()));
    }

    /**
     * Обработка ошибок транспорта.
     *      * @param session WebSocket сессия
     * @param exception Исключение
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", 
            session.getId(), exception.getMessage());
    }

    /**
     * Регистрирует WebSocket сессию для получения обновлений по correlation ID.
     *      * Вызывается из WeatherService после генерации correlationId.
     *      * @param correlationId Уникальный идентификатор запроса
     * @param session WebSocket сессия клиента
     */
    public void registerSession(String correlationId, WebSocketSession session) {
        activeSessions.put(correlationId, session);
        log.debug("Registered WebSocket session for correlation ID: {}", correlationId);
    }

    /**
     * Отправляет обновление клиенту через WebSocket.
     *      * Этот метод вызывается из WeatherService при получении:
     * - Промежуточных результатов (отдельные города)
     * - Финального агрегированного отчета
     *      * Формат сообщения:
     * {
     *   "type": "WEATHER_UPDATE" | "AGGREGATED_REPORT" | "ERROR",
     *   "data": { ... }
     * }
     *      * @param correlationId Идентификатор запроса
     * @param data Данные для отправки
     */
    public void sendUpdate(String correlationId, Object data) {
        WebSocketSession session = activeSessions.get(correlationId);

        if (session != null && session.isOpen()) {
            try {
            String jsonMessage = objectMapper.writeValueAsString(data);

                // *** КРИТИЧЕСКОЕ ИЗМЕНЕНИЕ: Синхронизация по объекту session обеспечивает, 
                // что только один поток может выполнять sendMessage для этого конкретного клиента 
                // в любой момент времени, предотвращая IllegalStateException. ***
                synchronized (session) {
                    session.sendMessage(new TextMessage(jsonMessage)); 
                }

                log.debug("📤 Sent update to client for correlation ID: {}", correlationId);

            } catch (IOException e) {
                log.error("Error sending WebSocket update: {}", e.getMessage());
            }
        } else {
            log.warn("No active WebSocket session found for correlation ID: {}", correlationId);
        }
    }

    /**
     * Закрывает WebSocket соединение и удаляет сессию.
     *      * Вызывается после отправки финального отчета.
     *      * @param correlationId Идентификатор запроса
     */
    public void closeSession(String correlationId) {
        WebSocketSession session = activeSessions.remove(correlationId);

        if (session != null && session.isOpen()) {
            try {
                // Отправляем финальное сообщение о завершении
                // *** Синхронизация здесь не обязательна, так как это последнее сообщение
                // и после него мы закрываем соединение, но для чистоты можно добавить.
                synchronized (session) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        Map.of(
                            "type", "CONNECTION_CLOSING",
                            "message", "All data received, closing connection"
                        )
                    )));
                }

                session.close(CloseStatus.NORMAL);
                log.info("✅ Closed WebSocket session for correlation ID: {}", correlationId);

            } catch (Exception e) {
                log.error("Error closing WebSocket session: {}", e.getMessage());
            }
        }
    }

    /**
     * Отправляет сообщение об ошибке клиенту.
     *      * @param session WebSocket сессия
     * @param errorMessage Текст ошибки
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            // Рекомендуется синхронизировать отправку сообщений об ошибках
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    Map.of(
                        "type", "ERROR",
                        "message", errorMessage
                    )
                )));
            }
        } catch (Exception e) {
            log.error("Error sending error message: {}", e.getMessage());
        }
    }
}