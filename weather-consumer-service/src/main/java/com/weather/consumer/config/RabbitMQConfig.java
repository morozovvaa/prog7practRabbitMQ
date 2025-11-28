package com.weather.consumer.config;

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
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация RabbitMQ для Weather Consumer Service.
 * 
 * ВАЖНО: Этот сервис ДОЛЖЕН создавать weather.request.queue с DLQ параметрами.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.queue.request}")
    private String requestQueueName;

    @Value("${rabbitmq.queue.response}")
    private String responseQueueName;

    @Value("${rabbitmq.exchange.weather}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key.response}")
    private String responseRoutingKey;

    @Value("${rabbitmq.routing-key.request}")
    private String requestRoutingKey;

    /**
     * ✅ Очередь для получения запросов на погоду с настройкой DLQ.
     * 
     * ВАЖНО: x-dead-letter-routing-key должен быть задан явно!
     */
    @Bean
    public Queue requestQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", exchangeName);
        args.put("x-dead-letter-routing-key", "weather.request.dlq");
        
        return QueueBuilder.durable(requestQueueName)
                .withArguments(args)
                .build();
    }

    /**
     * ✅ Dead Letter Queue для проблемных сообщений
     */
    @Bean
    public Queue deadLetterQueue() {
        return new Queue("weather.request.dlq", true);
    }

    /**
     * ✅ Binding для DLQ
     */
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange weatherExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(weatherExchange)
                .with("weather.request.dlq");
    }

    /**
     * Очередь для отправки ответов с данными о погоде
     */
    @Bean
    public Queue responseQueue() {
        return new Queue(responseQueueName, true);
    }

    /**
     * Topic Exchange для маршрутизации сообщений
     */
    @Bean
    public TopicExchange weatherExchange() {
        return new TopicExchange(exchangeName);
    }

    /**
     * ✅ Binding для очереди запросов
     */
    @Bean
    public Binding requestBinding(Queue requestQueue, TopicExchange weatherExchange) {
        return BindingBuilder
                .bind(requestQueue)
                .to(weatherExchange)
                .with(requestRoutingKey);
    }

    /**
     * Binding для очереди ответов
     */
    @Bean
    public Binding responseBinding(Queue responseQueue, TopicExchange weatherExchange) {
        return BindingBuilder
                .bind(responseQueue)
                .to(weatherExchange)
                .with(responseRoutingKey);
    }

    /**
     * JSON конвертер для сообщений
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate с JSON конвертером
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * RestTemplate для HTTP запросов к Weather API
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}