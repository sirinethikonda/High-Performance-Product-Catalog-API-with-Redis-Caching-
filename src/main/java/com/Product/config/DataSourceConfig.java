package com.Product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Value("${DATABASE_URL:postgresql://catalog_user:securepassword@localhost:5432/product_catalog}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        try {
            String cleanUrl = databaseUrl.replace("jdbc:", "");
            URI uri = new URI(cleanUrl);
            
            String userInfo = uri.getUserInfo();
            String username = "catalog_user";
            String password = "securepassword";
            
            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":");
                username = parts[0];
                password = parts[1];
            }
            
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = uri.getPath();
            
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
            
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(jdbcUrl);
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            
            return dataSource;
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct DataSource from DATABASE_URL: " + databaseUrl, e);
        }
    }
}
