package com.party.security.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** A client may refresh and retry only a CSRF rejection, before the controller ran. */
public final class ApiAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        String code = "ACCESS_DENIED";
        String message = "Your account is not allowed to perform this action.";
        if (exception instanceof MissingCsrfTokenException) {
            code = "CSRF_TOKEN_MISSING";
            message = "The CSRF cookie is missing. Refresh the security token and retry.";
        } else if (exception instanceof InvalidCsrfTokenException) {
            code = "CSRF_TOKEN_INVALID";
            message = "The CSRF token no longer matches this browser session. Refresh the security token and retry.";
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpServletResponse.SC_FORBIDDEN);
        body.put("error", "Forbidden");
        body.put("code", code);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        // Exception messages can contain the rejected token; never serialize them.
        mapper.writeValue(response.getOutputStream(), body);
    }
}
