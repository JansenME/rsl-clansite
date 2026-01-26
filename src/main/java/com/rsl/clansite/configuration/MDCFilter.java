package com.rsl.clansite.configuration;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 101)
public class MDCFilter extends OncePerRequestFilter {
    private static final String MDC_SESSION_KEY = "sessionId";

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {
        try {
            HttpSession session = request.getSession(false);

            if (session != null) {
                MDC.put(MDC_SESSION_KEY, session.getId());
            } else {
                MDC.put(MDC_SESSION_KEY, "-");
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_SESSION_KEY);
        }
    }
}