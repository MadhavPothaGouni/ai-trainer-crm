package com.aitrainercrm.platform.security.jwt;

import com.aitrainercrm.platform.common.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Runs when an unauthenticated request hits an endpoint that requires authentication - returns our standard ErrorResponse instead of Spring Security's default HTML/plain-text 401 page. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.of(
                "UNAUTHENTICATED",
                "Authentication is required to access this resource",
                HttpStatus.UNAUTHORIZED.value(),
                request.getRequestURI(),
                UUID.randomUUID().toString());

        objectMapper.writeValue(response.getWriter(), body);
    }
}
