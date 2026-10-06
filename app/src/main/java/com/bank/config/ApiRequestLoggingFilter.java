package com.bank.config;

import com.bank.security.JwtAuthInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            String query = request.getQueryString();
            String uri = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
            Object userAttr = request.getAttribute(JwtAuthInterceptor.ATTR_USERNAME);
            String user = userAttr != null ? userAttr.toString() : "-";
            Object tokenAttr = request.getAttribute(JwtAuthInterceptor.ATTR_TOKEN);
            String token = tokenAttr != null ? tokenAttr.toString() : "-";
            log.info("API request: method={} uri={} user={} token={} status={} duration={}ms",
                    request.getMethod(), uri, user, token, response.getStatus(), elapsedMs);
        }
    }
}