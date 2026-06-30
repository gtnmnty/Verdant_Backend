package com.verdant.salon_ecomm.config;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {
  @Override
  protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
    if (ex instanceof IllegalArgumentException) {
      return GraphqlErrorBuilder.newError(env)
          .message(ex.getMessage())
          .errorType(ErrorType.BAD_REQUEST)
          .build();
    }

    if (ex instanceof EntityNotFoundException) {
      return GraphqlErrorBuilder.newError(env)
          .message(ex.getMessage())
          .errorType(ErrorType.NOT_FOUND)
          .build();
    }

    return null;
  }
}