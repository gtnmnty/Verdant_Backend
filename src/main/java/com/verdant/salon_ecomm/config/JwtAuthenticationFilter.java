package com.verdant.salon_ecomm.config;

import com.verdant.salon_ecomm.services.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest httpRequest,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain chain
    ) throws ServletException, IOException {
        final String authHeader = httpRequest.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(httpRequest, response);
            return;
        }

        // Only token parsing and the user lookup live in the try, so an exception thrown further
        // down the chain (controllers, resolvers) is no longer misreported as a bad token.
        try {
            final String jwt = authHeader.substring(7); // cleaner than replace()
            final String userEmail = jwtService.extractUsername(jwt);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (userEmail != null && authentication == null) {
                // Throws UsernameNotFoundException when the token's subject no longer exists,
                // e.g. staff changed this account's email, so old access tokens stop working at once.
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                // Status is re-read from the DB on every request: a suspended, banned or
                // unverified account is left unauthenticated even if its JWT has not expired yet.
                if (jwtService.isTokenValid(jwt, userDetails)
                    && userDetails.isEnabled()
                    && userDetails.isAccountNonLocked()
                ) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpRequest));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            // Expired, tampered or orphaned token. The frontend's apiClient refreshes on 401,
            // so keep that status here rather than continuing the chain unauthenticated.
            SecurityContextHolder.clearContext();
            log.debug("Rejected JWT on {}: {}", httpRequest.getRequestURI(), e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid or expired token\"}");
            return;
        }

        // Hand the request on. Without this, every request with a valid token got an empty response.
        chain.doFilter(httpRequest, response);
    }
}
