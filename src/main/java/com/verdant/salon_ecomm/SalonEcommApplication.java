package com.verdant.salon_ecomm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SalonEcommApplication {
	public static void main(String[] args) {
		System.out.println(">>> DB_URL: " + System.getenv("DB_URL"));
		System.out.println(">>> DB_USERNAME: " + System.getenv("DB_USERNAME"));
		System.out.println(">>> ALL ENV VARS:");
		System.getenv().forEach((k, v) -> System.out.println(k + " = " + v));;
		SpringApplication.run(SalonEcommApplication.class, args);
	}
}
