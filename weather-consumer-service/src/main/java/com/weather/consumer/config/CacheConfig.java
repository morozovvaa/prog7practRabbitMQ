package com.weather.consumer.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Конфигурация кэша для Weather Consumer Service.
 * Использует Caffeine - высокопроизводительный in-memory кэш.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Создает CacheManager с настройками Caffeine.
     * 
     * Параметры:
     * - maximumSize(500): Максимум 500 записей в кэше
     * - expireAfterWrite(5, MINUTES): Данные устаревают через 5 минут после записи
     * - recordStats(): Включает статистику кэша (для мониторинга)
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("weather");
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)                      // Макс 500 городов в кэше
                .expireAfterWrite(5, TimeUnit.MINUTES) // TTL = 5 минут
                .recordStats());                       // Статистика для мониторинга
        
        return cacheManager;
    }
}