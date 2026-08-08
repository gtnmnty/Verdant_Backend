package com.verdant.salon_ecomm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.verdant.salon_ecomm.repositories")
public class SalonEcommApplication {
	public static void main(String[] args) {;
		SpringApplication.run(SalonEcommApplication.class, args);
	}
}

