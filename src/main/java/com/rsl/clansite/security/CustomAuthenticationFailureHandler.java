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

        String messageToUser = "The connection to Discord failed. Please tell Kloep (Kleoperd) on Discord to check the Client Secret.";

        if (exception instanceof OAuth2AuthenticationException oauth2Ex) {
            String errorCode = oauth2Ex.getError().getErrorCode();
            String description = oauth2Ex.getError().getDescription();

            if ("not_in_guild".equals(errorCode)) {
                messageToUser = (description != null) ? description : "Access Denied: You are not a member of the Clan Discord. If you are a member of the clan, please request access and try again.";
            }
        }

        String errorMessage = URLEncoder.encode(messageToUser, StandardCharsets.UTF_8);
        response.sendRedirect("/login?error=" + errorMessage);
    }
}