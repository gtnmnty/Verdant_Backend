package com.verdant.salon_ecomm.graphql;

import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// H-6 / H-7: when a Java enum value isn't declared in the GraphQL enum of the same name, serializing
// it fails, and on a non-null field (or inside a non-null list) the error wipes out the whole parent
// result. Every Java enum under models.enums that shares its simple name with a schema enum must
// have exactly the same values. Plain unit test: reads the .graphql files and scans the classpath.
class EnumSchemaParityTest {

    private static final String ENUM_PACKAGE = "com/verdant/salon_ecomm/models/enums";

    @Test
    void schemaEnumsMatchJavaEnums() throws Exception {
        Map<String, Set<String>> schemaEnums = loadSchemaEnums();
        Map<String, Class<?>> javaEnums = loadJavaEnums();

        // Guards the test itself: if the scan finds nothing, it would pass vacuously.
        assertThat(javaEnums).containsKeys("PaymentStatus", "NotificationType");

        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : schemaEnums.entrySet()) {
            Class<?> javaEnum = javaEnums.get(entry.getKey());
            if (javaEnum == null) continue; // schema-only enum (e.g. sort options with no Java twin)

            Set<String> javaValues = Arrays.stream(javaEnum.getEnumConstants())
                .map(c -> ((Enum<?>) c).name())
                .collect(Collectors.toCollection(TreeSet::new));
            Set<String> missingInSchema = new TreeSet<>(javaValues);
            missingInSchema.removeAll(entry.getValue());
            Set<String> missingInJava = new TreeSet<>(entry.getValue());
            missingInJava.removeAll(javaValues);

            if (!missingInSchema.isEmpty() || !missingInJava.isEmpty()) {
                mismatches.add(entry.getKey() + ": missing in schema " + missingInSchema
                    + ", missing in Java " + missingInJava);
            }
        }
        assertThat(mismatches).as("GraphQL vs Java enum mismatches").isEmpty();
    }

    private Map<String, Set<String>> loadSchemaEnums() throws IOException {
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:graphql/**/*.graphql*");
        assertThat(files).isNotEmpty();
        for (Resource file : files) {
            registry.merge(new SchemaParser().parse(file.getContentAsString(StandardCharsets.UTF_8)));
        }
        return registry.getTypes(EnumTypeDefinition.class).stream().collect(Collectors.toMap(
            EnumTypeDefinition::getName,
            e -> e.getEnumValueDefinitions().stream().map(EnumValueDefinition::getName).collect(Collectors.toSet())
        ));
    }

    private Map<String, Class<?>> loadJavaEnums() throws IOException, ClassNotFoundException {
        Map<String, Class<?>> result = new HashMap<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:" + ENUM_PACKAGE + "/**/*.class")) {
            // URLs always use '/', on Windows too.
            String path = r.getURL().toString();
            String className = path.substring(path.indexOf(ENUM_PACKAGE), path.length() - ".class".length())
                .replace('/', '.');
            if (className.contains("$")) continue;
            Class<?> type = Class.forName(className);
            if (type.isEnum()) result.put(type.getSimpleName(), type);
        }
        return result;
    }
}
