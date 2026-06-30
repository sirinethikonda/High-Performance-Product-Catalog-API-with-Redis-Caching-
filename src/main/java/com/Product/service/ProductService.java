package com.Product.service;

import com.Product.dto.ProductRequest;
import com.Product.dto.ProductUpdateRequest;
import com.Product.model.Product;
import com.Product.repository.ProductRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Value("${CACHE_TTL_SECONDS:60}")
    private long cacheTtlSeconds;

    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String PRODUCT_LIST_KEY_PREFIX = "products:list:";
    private static final String LIST_KEYS_SET = "products:list_keys";

    public Product createProduct(ProductRequest request) {
        validateCreateRequest(request);

        String id = UUID.randomUUID().toString();
        Product product = Product.builder()
                .id(id)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .build();

        Product savedProduct = productRepository.save(product);

        invalidateListCaches();

        return savedProduct;
    }

    public Product getProductById(String id) {
        String cacheKey = PRODUCT_KEY_PREFIX + id;

        try {
            String cachedJson = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                log.info("Cache hit for product ID: {}", id);
                return objectMapper.readValue(cachedJson, Product.class);
            }
        } catch (Exception e) {
            log.error("Redis connection failed during read. Falling back to DB.", e);
        }

        log.info("Cache miss for product ID: {}", id);
        Product product = productRepository.findById(id).orElse(null);
        if (product != null) {
            try {
                String json = objectMapper.writeValueAsString(product);
                redisTemplate.opsForValue().set(cacheKey, json, cacheTtlSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Redis connection failed during write.", e);
            }
        }
        return product;
    }

    public List<Product> getProducts(int limit, int offset) {
        String cacheKey = PRODUCT_LIST_KEY_PREFIX + limit + ":" + offset;

        try {
            String cachedJson = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                log.info("Cache hit for product list: limit={}, offset={}", limit, offset);
                return objectMapper.readValue(cachedJson, new TypeReference<List<Product>>() {});
            }
        } catch (Exception e) {
            log.error("Redis connection failed during list read. Falling back to DB.", e);
        }

        log.info("Cache miss for product list: limit={}, offset={}", limit, offset);
        List<Product> products = productRepository.findAllWithOffset(limit, offset);

        try {
            String json = objectMapper.writeValueAsString(products);
            redisTemplate.opsForValue().set(cacheKey, json, cacheTtlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForSet().add(LIST_KEYS_SET, cacheKey);
        } catch (Exception e) {
            log.error("Redis connection failed during list write.", e);
        }

        return products;
    }

    public Product updateProduct(String id, ProductUpdateRequest request) {
        Product existingProduct = productRepository.findById(id).orElse(null);
        if (existingProduct == null) {
            return null;
        }

        validateUpdateRequest(request);

        if (request.getName() != null) {
            existingProduct.setName(request.getName());
        }
        if (request.getDescription() != null) {
            existingProduct.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            existingProduct.setPrice(request.getPrice());
        }
        if (request.getStockQuantity() != null) {
            existingProduct.setStockQuantity(request.getStockQuantity());
        }

        Product updatedProduct = productRepository.save(existingProduct);

        try {
            redisTemplate.delete(PRODUCT_KEY_PREFIX + id);
        } catch (Exception e) {
            log.error("Redis connection failed during single cache delete.", e);
        }

        invalidateListCaches();

        return updatedProduct;
    }

    public boolean deleteProduct(String id) {
        if (!productRepository.existsById(id)) {
            return false;
        }

        productRepository.deleteById(id);

        try {
            redisTemplate.delete(PRODUCT_KEY_PREFIX + id);
        } catch (Exception e) {
            log.error("Redis connection failed during single cache delete.", e);
        }

        invalidateListCaches();

        return true;
    }

    private void invalidateListCaches() {
        try {
            Set<Object> keys = redisTemplate.opsForSet().members(LIST_KEYS_SET);
            if (keys != null && !keys.isEmpty()) {
                List<String> stringKeys = new ArrayList<>();
                for (Object k : keys) {
                    stringKeys.add((String) k);
                }
                redisTemplate.delete(stringKeys);
            }
            redisTemplate.delete(LIST_KEYS_SET);
            log.info("Invalidated all paginated product lists");
        } catch (Exception e) {
            log.error("Redis connection failed during list cache invalidation.", e);
        }
    }

    private void validateCreateRequest(ProductRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        if (request.getPrice() == null) {
            throw new IllegalArgumentException("Price is required");
        }
        if (request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (request.getStockQuantity() == null) {
            throw new IllegalArgumentException("Stock quantity is required");
        }
        if (request.getStockQuantity() < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
    }

    private void validateUpdateRequest(ProductUpdateRequest request) {
        if (request.getName() != null && request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        if (request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (request.getStockQuantity() != null && request.getStockQuantity() < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
    }
}
