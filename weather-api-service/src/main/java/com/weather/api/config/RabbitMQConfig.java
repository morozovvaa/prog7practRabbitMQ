package com.weather.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация RabbitMQ для Weather API Service.
 * 
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.queue.aggregated}")
    private String aggregatedQueueName;

    @Value("${rabbitmq.exchange.weather}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key.request}")
    private String requestRoutingKey;

    @Value("${rabbitmq.routing-key.aggregated}")
    private String aggregatedRoutingKey;


    /**
     * Очередь для получения агрегированных результатов.
     */
    @Bean
    public Queue aggregatedQueue() {
        return new Queue(aggregatedQueueName, true);
    }

    /**
     * НОВОЕ: Очередь для индивидуальных результатов (WebSocket режим).
     */
    @Bean
    public Queue individualResponseQueue() {
        return new Queue("weather.individual.response.queue", true);
    }

    /**
     * Topic Exchange для маршрутизации сообщений по routing key.
     */
    @Bean
    public TopicExchange weatherExchange() {
        return new TopicExchange(exchangeName);
    }


    /**
     * Binding для связи очереди агрегированных результатов с exchange.
     */
    @Bean
    public Binding aggregatedBinding(Queue aggregatedQueue, TopicExchange weatherExchange) {
        return BindingBuilder
                .bind(aggregatedQueue)
                .to(weatherExchange)
                .with(aggregatedRoutingKey);
    }

    /**
     * НОВОЕ: Binding для очереди индивидуальных результатов
     */
    @Bean
    public Binding individualResponseBinding(Queue individualResponseQueue, TopicExchange weatherExchange) {
        return BindingBuilder
                .bind(individualResponseQueue)
                .to(weatherExchange)
                .with("weather.individual.response");
    }

    /**
     * Конвертер сообщений для автоматической сериализации/десериализации в JSON.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate с настроенным JSON конвертером.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}