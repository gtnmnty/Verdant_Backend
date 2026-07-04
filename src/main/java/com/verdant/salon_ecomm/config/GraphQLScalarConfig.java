package com.verdant.salon_ecomm.config;

import graphql.scalars.ExtendedScalars;
import graphql.schema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.web.multipart.MultipartFile;

@Configuration
public class GraphQLScalarConfig {

  @Bean
  RuntimeWiringConfigurer runtimeWiringConfigurer() {
    return wiring -> wiring
        .scalar(ExtendedScalars.DateTime)
        .scalar(ExtendedScalars.GraphQLBigDecimal);
  }
}
