package com.Product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductUpdateRequest {

    private String name;
    private String description;
    private BigDecimal price;

    @JsonProperty("stock_quantity")
    private Integer stockQuantity;
}
