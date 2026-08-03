package com.verdant.salon_ecomm.config;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import graphql.scalars.ExtendedScalars;
import graphql.schema.*;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Configuration
public class GraphQLScalarConfig {

    @Bean
    public GraphQLScalarType uploadScalar() {
        return GraphQLScalarType.newScalar()
            .name("Upload")
            .description("A file part in a multipart request")
            .coercing(new Coercing<MultipartFile, Void>() {
                @Override
                public Void serialize(
                    @NonNull Object dataFetcherResult,
                    @NonNull GraphQLContext context,
                    @NonNull Locale locale
                ) throws CoercingSerializeException {
                    throw new CoercingSerializeException("Upload is an input-only type");
                }

                @Override
                public MultipartFile parseValue(
                    @NonNull Object input,
                    @NonNull GraphQLContext context,
                    @NonNull Locale locale
                ) throws CoercingParseValueException {
                    if (input instanceof MultipartFile multipartFile) {
                        return multipartFile;
                    }
                    throw new CoercingParseValueException(
                        "Expected type MultipartFile but was " +
                            input.getClass().getName()
                    );
                }

                @Override
                public MultipartFile parseLiteral(
                    @NonNull Value<?> input,
                    @NonNull CoercedVariables variables,
                    @NonNull GraphQLContext context,
                    @NonNull Locale locale
                ) throws CoercingParseLiteralException {
                    throw new CoercingParseLiteralException("Must use variables to specify Upload values");
                }
            })
            .build();
    }

    @Bean
    RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiring -> wiring
            .scalar(ExtendedScalars.DateTime)
            .scalar(ExtendedScalars.Date)
            .scalar(ExtendedScalars.GraphQLBigDecimal)
            .scalar(ExtendedScalars.UUID)
            .scalar(uploadScalar());
    }
}