package com.thinkxecai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Gives Spring MVC's JSON converter (request bodies in, responses out) its own copy of the ObjectMapper
 * with {@link NulFreeStringDeserializer}. The shared ObjectMapper stays unchanged: HTTP clients built from
 * Spring Boot's RestClient.Builder (the chat models) read their replies with it.
 */
@Configuration
public class RequestBodyJsonConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                ObjectMapper requestMapper = jackson.getObjectMapper().copy()
                        .registerModule(new SimpleModule("NulFreeRequestText")
                                .addDeserializer(String.class, new NulFreeStringDeserializer()));
                jackson.setObjectMapper(requestMapper);
            }
        }
    }

}
