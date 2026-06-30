package com.Product.repository;

import com.Product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    @Query(value = "SELECT * FROM products ORDER BY id ASC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Product> findAllWithOffset(@Param("limit") int limit, @Param("offset") int offset);
}
