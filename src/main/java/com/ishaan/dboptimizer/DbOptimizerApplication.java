package com.ishaan.dboptimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DbOptimizerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DbOptimizerApplication.class, args);
	}

}
