package com.saleha.promptservice;

import com.saleha.promptservice.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PromptServiceApplication {

	public static void main(String[] args) {
		DotenvLoader.load();
		SpringApplication.run(PromptServiceApplication.class, args);
	}

}
