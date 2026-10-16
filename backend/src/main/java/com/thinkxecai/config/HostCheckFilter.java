package com.thinkxecai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkxecai.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Serves only requests addressed to {@code localhost} or {@code 127.0.0.1} (any port). The server
 * listens on loopback only, but a website can still reach it through DNS rebinding: its own name is
 * made to resolve to 127.0.0.1, and the browser then treats the API as the same site. Such a request
 * carries the website's name as its Host, so it is refused here, before CORS or any controller (ADR 033).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class HostCheckFilter extends OncePerRequestFilter {

    static final Set<String> ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1");

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // The servlet container takes the server name from the Host header, without the port.
        if (ALLOWED_HOSTS.contains(request.getServerName().toLowerCase(Locale.ROOT))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Refused a request with a non-local Host header");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(403, "Host not allowed"));
    }

}
