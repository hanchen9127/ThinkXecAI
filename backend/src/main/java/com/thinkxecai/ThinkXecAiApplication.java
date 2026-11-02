package com.thinkxecai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Locale;

@SpringBootApplication
public class ThinkXecAiApplication {

    public static void main(String[] args) {
        useEnglishMessages();
        SpringApplication.run(ThinkXecAiApplication.class, args);
    }

    /**
     * Libraries word their messages in the JVM's default language, and the PostgreSQL driver's reach the
     * model and the UI (a failed query reads "Position: 37", or "位置：37" on a Chinese system). The driver
     * picks its language once, at its first message, so this runs before anything else (ADR 056).
     */
    static void useEnglishMessages() {
        Locale.setDefault(Locale.ENGLISH);
    }

}
