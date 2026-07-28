package com.saleha.reviewservice;

import com.saleha.reviewservice.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReviewServiceApplication {

	public static void main(String[] args) {
		DotenvLoader.load();
		SpringApplication.run(ReviewServiceApplication.class, args);
	}

}
