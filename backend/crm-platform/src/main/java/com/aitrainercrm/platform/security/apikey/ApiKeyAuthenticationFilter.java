package com.aitrainercrm.platform.security.apikey;

import com.aitrainercrm.platform.apikey.service.ApiKeyService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The programmatic-auth counterpart to {@code JwtAuthenticationFilter}:
 * runs once per request, ahead of the rest of the Spring Security chain,
 * looking for an {@code X-Api-Key} header instead of an {@code Authorization}
 * bearer token. A request could in principle carry both headers; this
 * filter only acts if nothing has already authenticated the request (see
 * the {@code getAuthentication() == null} check), so whichever filter runs
 * first wins rather than one silently overwriting the other. A missing,
 * malformed, revoked, or expired key simply leaves the context empty -
 * same "let the security chain's authorization rules decide" behavior as
 * the JWT filter, not an immediate 401 from here.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey != null && !rawKey.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            apiKeyService.authenticate(rawKey.trim()).map(UserPrincipal::new).ifPresent(principal -> {
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
