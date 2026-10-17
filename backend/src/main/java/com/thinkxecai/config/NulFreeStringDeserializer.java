package com.thinkxecai.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;

/**
 * PostgreSQL cannot store the NUL character (U+0000) in text or jsonb, so a request text containing it
 * used to fail at the insert with a generic 500. Every JSON text value of a request body is checked while
 * it is read, and the request is refused with 400 naming the field ({@code name: invalid value}) before any
 * service runs. Registered only for request bodies ({@link RequestBodyJsonConfig}), not on the shared
 * ObjectMapper, which also reads chat-model replies.
 */
public class NulFreeStringDeserializer extends StdScalarDeserializer<String> {

    public NulFreeStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = StringDeserializer.instance.deserialize(parser, context);
        if (value != null && value.indexOf('\u0000') >= 0) {
            throw InvalidFormatException.from(parser, "Text must not contain NUL characters", value, String.class);
        }
        return value;
    }

}
