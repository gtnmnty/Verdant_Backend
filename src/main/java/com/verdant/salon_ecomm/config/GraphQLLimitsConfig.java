package com.verdant.salon_ecomm.config;

import graphql.ExecutionResult;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.Field;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// M-8: caps how deep and how big one GraphQL operation can be. Without them a single request like
// services { stylists { services { stylists { ... } } } }, or one field repeated under hundreds of
// aliases, made the server resolve an unbounded tree.
// Flow: Spring Boot adds every Instrumentation bean to the GraphQL engine -> before execution each
// one walks the parsed query -> over the limit: the request is rejected with an error, nothing runs.
// Page sizes are capped separately in PageRequests (DEFAULT_MAX_SIZE = 100).
// Limits were set from the frontend's own queries: deepest is about 6 levels and largest about
// 35 fields, so 10 / 500 leave plenty of room. Both can be changed per environment.
@Configuration
public class GraphQLLimitsConfig {

    @Bean
    public Instrumentation maxQueryDepthInstrumentation(@Value("${app.graphql.max-depth:10}") int maxDepth) {
        return new MaxQueryDepthInstrumentation(maxDepth) {
            @Override
            public InstrumentationContext<ExecutionResult> beginExecuteOperation(
                InstrumentationExecuteOperationParameters parameters, InstrumentationState state
            ) {
                if (isIntrospectionOnly(parameters.getExecutionContext())) return SimpleInstrumentationContext.noOp();
                return super.beginExecuteOperation(parameters, state);
            }
        };
    }

    // Default calculator: every selected field costs 1 (its children add to it), so this is roughly
    // "fields in the query, after fragments and aliases are expanded".
    @Bean
    public Instrumentation maxQueryComplexityInstrumentation(@Value("${app.graphql.max-complexity:500}") int maxComplexity) {
        return new MaxQueryComplexityInstrumentation(maxComplexity) {
            @Override
            public InstrumentationContext<ExecutionResult> beginExecuteOperation(
                InstrumentationExecuteOperationParameters parameters, InstrumentationState state
            ) {
                if (isIntrospectionOnly(parameters.getExecutionContext())) return SimpleInstrumentationContext.noOp();
                return super.beginExecuteOperation(parameters, state);
            }
        };
    }

    // GraphiQL's schema query is about 12 levels deep (nested ofType), so the limits above would break
    // it in the local profile. Pure introspection is exempt: it is disabled outside local
    // (spring.graphql.schema.introspection.enabled) and graphql-java caps it on its own
    // (GoodFaithIntrospection). An operation that mixes __schema with real fields is still checked.
    private static boolean isIntrospectionOnly(ExecutionContext context) {
        var selections = context.getOperationDefinition().getSelectionSet().getSelections();
        return !selections.isEmpty() && selections.stream()
            .allMatch(s -> s instanceof Field f && f.getName().startsWith("__"));
    }
}
