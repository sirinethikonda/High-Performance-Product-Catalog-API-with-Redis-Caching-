package com.Product;

import com.Product.dto.ProductRequest;
import com.Product.dto.ProductUpdateRequest;
import com.Product.model.Product;
import com.Product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "RATE_LIMIT_MAX_REQUESTS=10",
        "RATE_LIMIT_WINDOW_SECONDS=60",
        "CACHE_TTL_SECONDS=10"
})
public class ProductCatalogIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        productRepository.deleteAll();
        try {
            Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        } catch (Exception e) {
            System.err.println("Redis flush failed: " + e.getMessage());
        }
    }

    @Test
    public void testProductCrudOperations() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Test Product")
                .description("Test Description")
                .price(new BigDecimal("99.99"))
                .stockQuantity(10)
                .build();

        String responseContent = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Test Product")))
                .andExpect(jsonPath("$.price", is(99.99)))
                .andExpect(jsonPath("$.stock_quantity", is(10)))
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        Product createdProduct = objectMapper.readValue(responseContent, Product.class);
        String id = createdProduct.getId();

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.name", is("Test Product")));

        ProductUpdateRequest updateRequest = ProductUpdateRequest.builder()
                .name("Updated Product Name")
                .price(new BigDecimal("79.99"))
                .build();

        mockMvc.perform(put("/api/v1/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Product Name")))
                .andExpect(jsonPath("$.price", is(79.99)));

        mockMvc.perform(delete("/api/v1/products/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testPagination() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Product p = Product.builder()
                    .id("uuid-" + i)
                    .name("Product " + i)
                    .description("Desc " + i)
                    .price(new BigDecimal("10.00").multiply(BigDecimal.valueOf(i)))
                    .stockQuantity(i)
                    .build();
            productRepository.save(p);
        }

        mockMvc.perform(get("/api/v1/products?limit=2&offset=0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("uuid-1")))
                .andExpect(jsonPath("$[1].id", is("uuid-2")));

        mockMvc.perform(get("/api/v1/products?limit=2&offset=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("uuid-3")))
                .andExpect(jsonPath("$[1].id", is("uuid-4")));
    }

    @Test
    public void testCachingAndInvalidation() throws Exception {
        String id = UUID.randomUUID().toString();
        Product product = Product.builder()
                .id(id)
                .name("Cache Product")
                .description("Initial Desc")
                .price(new BigDecimal("50.00"))
                .stockQuantity(5)
                .build();
        productRepository.save(product);

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Cache Product")));

        product.setName("Bypassed Name");
        productRepository.save(product);

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Cache Product")));

        ProductUpdateRequest updateRequest = ProductUpdateRequest.builder()
                .name("Updated Via API")
                .build();

        mockMvc.perform(put("/api/v1/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Via API")));

        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Via API")));
    }

    @Test
    public void testRateLimiting() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/v1/products?limit=1"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/products?limit=1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error", is("Too Many Requests")))
                .andExpect(jsonPath("$.message", containsString("Rate limit exceeded")));
    }

    @Test
    public void testValidationErrors() throws Exception {
        ProductRequest invalidRequest = ProductRequest.builder()
                .name("Invalid Product")
                .price(new BigDecimal("-10.00"))
                .stockQuantity(5)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("negative")));
    }
}
