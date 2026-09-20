package com.ishaan.dboptimizer.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.target")
    public HikariDataSource targetDataSource() {
        return new HikariDataSource();
    }

    @Bean
    @ConfigurationProperties("app.datasource.knowledge")
    public HikariDataSource knowledgeDataSource() {
        return new HikariDataSource();
    }

    @Bean
    public JdbcClient targetJdbcClient(DataSource targetDataSource) {
        return JdbcClient.create(targetDataSource);
    }   
    @Bean
    public JdbcClient knowledgeJdbcClient(DataSource knowledgeDataSource) {
        return JdbcClient.create(knowledgeDataSource);
    }
}