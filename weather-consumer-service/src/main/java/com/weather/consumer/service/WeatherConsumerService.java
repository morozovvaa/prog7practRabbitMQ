package com.weather.consumer.service;

import com.rabbitmq.client.Channel;
import com.weather.consumer.client.WeatherApiClient;
import com.weather.consumer.dto.OpenWeatherMapResponse;
import com.weather.consumer.dto.WeatherMessage;
import com.weather.consumer.dto.WeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Сервис для обработки сообщений из очереди запросов.
 * Реализует синхронную обработку с задержкой между запросами.
 * 
 * Алгоритм работы:
 * 1. Получает сообщение из очереди weather.request.queue
 * 2. Делает задержку (имитация rate limiting)
 * 3. Вызывает OpenWeatherMap API для получения данных о погоде
 * 4. Формирует ответное сообщение
 * 5. Отправляет ответ в очередь weather.response.queue
 * 6. Подтверждает обработку сообщения (manual acknowledgment)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherConsumerService {

    private final WeatherApiClient weatherApiClient;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.weather}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key.response}")
    private String responseRoutingKey;

    @Value("${weather.api.delay}")
    private long apiDelay;

    /**
     * Слушатель очереди запросов на получение погоды.
     * Использует ручное подтверждение (manual acknowledgment) для контроля обработки.
     * 
     * Параметры аннотации:
     * - queues: имя очереди для прослушивания
     * - ackMode: режим подтверждения (MANUAL для ручного управления)
     * 
     * @param weatherMessage Сообщение с запросом на получение погоды
     * @param message Оригинальное AMQP сообщение (для подтверждения)
     * @param channel Канал RabbitMQ (для подтверждения)
     */
    @RabbitListener(queues = "${rabbitmq.queue.request}")
    public void consumeWeatherRequest(WeatherMessage weatherMessage, Message message, Channel channel) {
        log.info("Received weather request for city: {} (correlation ID: {})",
                weatherMessage.getCity(), weatherMessage.getCorrelationId());

        WeatherResponse response = new WeatherResponse();
        response.setCorrelationId(weatherMessage.getCorrelationId());
        response.setCity(weatherMessage.getCity());
        response.setTotalCities(weatherMessage.getTotalCities());
        response.setTimestamp(LocalDateTime.now());

        try {
            // Задержка для имитации rate limiting и предотвращения перегрузки API
            log.debug("Applying delay of {} ms before API call", apiDelay);
            Thread.sleep(apiDelay);

            // Вызов Weather API
            OpenWeatherMapResponse apiResponse = weatherApiClient.getWeatherForCity(weatherMessage.getCity());

            // Заполнение успешного ответа
            response.setSuccess(true);
            response.setTemperature(apiResponse.getMain().getTemp());
            response.setHumidity(apiResponse.getMain().getHumidity());
            response.setWindSpeed(apiResponse.getWind().getSpeed());
            
            // Получение описания погоды (первый элемент из списка weather)
            if (apiResponse.getWeather() != null && !apiResponse.getWeather().isEmpty()) {
                response.setDescription(apiResponse.getWeather().get(0).getDescription());
            } else {
                response.setDescription("No description available");
            }

            log.info("Successfully fetched weather for {}: {}°C, {}",
                    weatherMessage.getCity(),
                    response.getTemperature(),
                    response.getDescription());

        } catch (InterruptedException e) {
            log.error("Thread interrupted during delay: {}", e.getMessage());
            Thread.currentThread().interrupt();
            response.setSuccess(false);
            response.setErrorMessage("Processing interrupted: " + e.getMessage());

        } catch (Exception e) {
            log.error("Error fetching weather for city {}: {}", weatherMessage.getCity(), e.getMessage());
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
        }

        try {
            // Отправка ответа в очередь
            log.debug("Sending weather response to queue for city: {}", weatherMessage.getCity());
            rabbitTemplate.convertAndSend(exchangeName, responseRoutingKey, response);

            // Подтверждение успешной обработки сообщения
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            log.debug("Message acknowledged for city: {}", weatherMessage.getCity());

        } catch (Exception e) {
            log.error("Error sending response or acknowledging message: {}", e.getMessage());
            try {
                // В случае ошибки отклоняем сообщение и возвращаем в очередь, requeue=false отправит сообщение в DLQ
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
                log.warn("Message sent to DLQ for city: {}", weatherMessage.getCity());
            } catch (Exception nackException) {
                log.error("Error sending NACK: {}", nackException.getMessage());
            }
        }
    }
}
