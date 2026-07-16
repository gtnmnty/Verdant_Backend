package com.verdant.salon_ecomm;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.verdant.salon_ecomm.repositories")
public class SalonEcommApplication {
	public static void main(String[] args) {
		System.out.println(">>> DB_URL: " + System.getenv("DB_URL"));
		System.out.println(">>> DB_USERNAME: " + System.getenv("DB_USERNAME"));
		System.out.println(">>> ALL ENV VARS:");
		System.getenv().forEach((k, v) -> System.out.println(k + " = " + v));;
		SpringApplication.run(SalonEcommApplication.class, args);
	}

	@Bean
	public DataFetcherExceptionResolverAdapter exceptionResolver() {
		return new DataFetcherExceptionResolverAdapter() {
			@Override
			protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
				// This forces the backend to send the ACTUAL Java error message to Postman
				return GraphqlErrorBuilder.newError(env)
						.message(ex.getMessage() != null ? ex.getMessage() : ex.toString())
						.build();
			}
		};
	}
	
}

