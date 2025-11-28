package com.weather.aggregator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.queue.response}")
    private String responseQueueName;

    @Value("${rabbitmq.queue.aggregated}")
    private String aggregatedQueueName;

    @Value("${rabbitmq.exchange.weather}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key.aggregated}")
    private String aggregatedRoutingKey;

    @Bean
    public Queue responseQueue() {
        return new Queue(responseQueueName, true);
    }

    @Bean
    public Queue aggregatedQueue() {
        return new Queue(aggregatedQueueName, true);
    }

    /**
     * КЛЮЧЕВАЯ ОЧЕРЕДЬ: Для индивидуальных результатов (WebSocket режим)
     */
    @Bean
    public Queue individualResponseQueue() {
        return new Queue("weather.individual.response.queue", true);
    }

    @Bean
    public TopicExchange weatherExchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Binding aggregatedBinding(Queue aggregatedQueue, TopicExchange weatherExchange) {
        return BindingBuilder
                .bind(aggregatedQueue)
                .to(weatherExchange)
                .with(aggregatedRoutingKey);
    }

    /**
     * КЛЮЧЕВОЙ BINDING: Связывает индивидуальную очередь с exchange
     */
    @Bean
    public Binding individualResponseBinding(Queue individualResponseQueue, TopicExchange weatherExchange) {
        return BindingBuilder
                .bind(individualResponseQueue)
                .to(weatherExchange)
                .with("weather.individual.response");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}