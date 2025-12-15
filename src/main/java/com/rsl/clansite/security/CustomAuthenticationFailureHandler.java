package com.rsl.clansite.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        if(exception instanceof OAuth2AuthenticationException oauth2Ex) {
            if ("unlinked_account".equals(oauth2Ex.getError().getErrorCode())) {

                String errorMessage = URLEncoder.encode(oauth2Ex.getError().getDescription(), StandardCharsets.UTF_8);

                response.sendRedirect("/login-error/unlinked?error=" + errorMessage);
                return;
            }

            response.sendRedirect("/login-error");
        }
    }
}
