package com.codechallenge.loginprocessingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class LoginProcessingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoginProcessingServiceApplication.class, args);
	}

}
