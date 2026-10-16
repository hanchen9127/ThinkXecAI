package com.thinkxecai.model.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** Every request field's size limit (ADR 032), pinned to the numbers in the spec: the maximum passes, one more fails. */
class RequestSizeLimitTest {

    // English like the application (fixed locale), whatever the JVM default is
    private static final Validator VALIDATOR = Validation.byProvider(HibernateValidator.class).configure()
            .defaultLocale(Locale.ENGLISH).buildValidatorFactory().getValidator();

    static Stream<Arguments> limits() {
        return Stream.of(
                text(CreateAgentRequest::new, "name", 200),
                text(CreateAgentRequest::new, "description", 2_000),
                text(CreateAgentRequest::new, "systemPrompt", 20_000),
                list(CreateAgentRequest::new, "allowedTools", 50, n -> Collections.nCopies(n, "emailTool")),
                list(CreateAgentRequest::new, "allowedKbs", 50, RequestSizeLimitTest::uuids),
                text(UpdateAgentRequest::new, "name", 200),
                text(UpdateAgentRequest::new, "description", 2_000),
                text(UpdateAgentRequest::new, "systemPrompt", 20_000),
                list(UpdateAgentRequest::new, "allowedTools", 50, n -> Collections.nCopies(n, "emailTool")),
                list(UpdateAgentRequest::new, "allowedKbs", 50, RequestSizeLimitTest::uuids),
                text(CreateKnowledgeBaseRequest::new, "name", 200),
                text(CreateKnowledgeBaseRequest::new, "description", 2_000),
                text(UpdateKnowledgeBaseRequest::new, "name", 200),
                text(UpdateKnowledgeBaseRequest::new, "description", 2_000),
                text(CreateDocumentRequest::new, "name", 200),
                text(UpdateDocumentRequest::new, "name", 200),
                text(CreateChatSessionRequest::new, "title", 200),
                text(UpdateChatSessionRequest::new, "title", 200),
                text(CreateChatMessageRequest::new, "content", 20_000));
    }

    @ParameterizedTest(name = "{0}.{1} at most {2}")
    @MethodSource("limits")
    void theMaximumPassesAndOneMoreIsRejected(String type, String field, int max, Supplier<Object> request,
                                              IntFunction<Object> valueOfSize) {
        assertThat(violations(request.get(), field, valueOfSize.apply(max))).isEmpty();
        assertThat(violations(request.get(), field, valueOfSize.apply(max + 1)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("size must be between 0 and " + max);
    }

    private static Set<ConstraintViolation<Object>> violations(Object request, String field, Object value) {
        new BeanWrapperImpl(request).setPropertyValue(field, value);
        return VALIDATOR.validateProperty(request, field);
    }

    private static Arguments text(Supplier<Object> request, String field, int max) {
        return arguments(request.get().getClass().getSimpleName(), field, max, request, (IntFunction<Object>) "x"::repeat);
    }

    private static Arguments list(Supplier<Object> request, String field, int max, IntFunction<Object> listOfSize) {
        return arguments(request.get().getClass().getSimpleName(), field, max, request, listOfSize);
    }

    private static List<UUID> uuids(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

}
