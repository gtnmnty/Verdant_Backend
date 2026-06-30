package com.verdant.salon_ecomm.config;

import com.verdant.salon_ecomm.entities.User;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class GraphQLAuthContextInterceptor implements WebGraphQlInterceptor {

  @Override
  public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof User user) {
      UUID userId = user.getId();
      request.configureExecutionInput((executionInput, builder) ->
              builder.graphQLContext(ctx -> ctx.put("userId", userId)).build()
      );
    }

    return chain.next(request);
  }
}