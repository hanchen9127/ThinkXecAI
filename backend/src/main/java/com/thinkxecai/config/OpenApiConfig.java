package com.thinkxecai.config;

import io.swagger.v3.oas.models.media.Schema;
import jakarta.annotation.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes the generated API description match what Jackson actually sends, so the frontend types
 * generated from it are exact:
 * <ul>
 *   <li>Every field of a response object is always present (Jackson writes nulls too), so all
 *       properties of {@code *VO} schemas, and of the nested types that are always complete in
 *       responses, are marked required.</li>
 *   <li>A field annotated {@code @Nullable} may be null, so null is added to its type.</li>
 * </ul>
 * Request schemas keep the required list derived from their Bean Validation annotations.
 */
@Configuration
public class OpenApiConfig {

    /** Nested response types whose fields are always present; ChatMessageMetadata is not (it omits nulls). */
    static final Set<String> ALWAYS_COMPLETE = Set.of("ChatOptions", "ToolCallMetadata", "ToolResponseMetadata");

    @Bean
    public OpenApiCustomizer responseFieldsAreRequired() {
        return openApi -> openApi.getComponents().getSchemas().forEach((name, schema) -> {
            if ((name.endsWith("VO") || ALWAYS_COMPLETE.contains(name)) && schema.getProperties() != null) {
                schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            }
        });
    }

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public PropertyCustomizer nullableFields() {
        return (property, type) -> {
            boolean nullable = type.getCtxAnnotations() != null
                    && Arrays.stream(type.getCtxAnnotations()).anyMatch(Nullable.class::isInstance);
            if (!nullable) {
                return property;
            }
            if (property.get$ref() != null) {
                // A reference cannot carry other keywords: say "the referenced object or null".
                Schema nullSchema = new Schema();
                nullSchema.addType("null");
                return new Schema().oneOf(List.of(new Schema().$ref(property.get$ref()), nullSchema));
            }
            Set<String> types = new LinkedHashSet<>();
            if (property.getTypes() != null) {
                types.addAll(property.getTypes());
            } else if (property.getType() != null) {
                types.add(property.getType());
            }
            types.add("null");
            property.setTypes(types);
            return property;
        };
    }

}
