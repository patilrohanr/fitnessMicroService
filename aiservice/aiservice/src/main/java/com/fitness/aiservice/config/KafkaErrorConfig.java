package com.fitness.aiservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Slf4j
@Configuration
public class KafkaErrorConfig {

    @Bean
    public DefaultErrorHandler errorHandler() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
        backOff.setInitialInterval(30_000L);   // 30s
        backOff.setMultiplier(2.0);            // then 60s
        backOff.setMaxInterval(120_000L);

        // After retries are exhausted: log and skip instead of blocking the partition
        return new DefaultErrorHandler((record, ex) ->
                log.error("Giving up on record offset={} key={}: {}",
                        record.offset(), record.key(), ex.getMessage()), backOff);
    }
}