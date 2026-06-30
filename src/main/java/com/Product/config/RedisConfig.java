package com.Product.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;

@Configuration
public class RedisConfig {

    @Value("${REDIS_URL:redis://localhost:6379/0}")
    private String redisUrl;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        try {
            URI uri = new URI(redisUrl);
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(uri.getHost());
            config.setPort(uri.getPort() == -1 ? 6379 : uri.getPort());
            
            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                String password = userInfo;
                if (userInfo.contains(":")) {
                    password = userInfo.split(":")[1];
                }
                config.setPassword(password);
            }
            
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                try {
                    int dbIndex = Integer.parseInt(path.substring(1));
                    config.setDatabase(dbIndex);
                } catch (NumberFormatException ignored) {}
            }
            
            return new LettuceConnectionFactory(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct Redis Connection Factory from REDIS_URL: " + redisUrl, e);
        }
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
        return mapper;
    }
}
