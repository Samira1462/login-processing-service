package com.codechallenge.loginprocessingservice;

import org.springframework.boot.SpringApplication;

public class TestLoginProcessingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(LoginProcessingServiceApplication::main).with(ContainersConfig.class).run(args);
	}

}
