package com.rsl.clansite.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableMongoHttpSession(maxInactiveIntervalInSeconds = 31536000)
public class SecurityConfig {
    private static final int SESSION_TIMEOUT_SECONDS = 31536000;

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService;
    private final CustomAuthenticationFailureHandler failureHandler;
    private final SessionSecurityFilter sessionSecurityFilter;
    private final AppTokenAuthenticationFilter appTokenFilter; // <--- NEW

    public SecurityConfig(OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService,
                          CustomAuthenticationFailureHandler failureHandler,
                          SessionSecurityFilter sessionSecurityFilter,
                          AppTokenAuthenticationFilter appTokenFilter) { // <--- NEW
        this.customOAuth2UserService = customOAuth2UserService;
        this.failureHandler = failureHandler;
        this.sessionSecurityFilter = sessionSecurityFilter;
        this.appTokenFilter = appTokenFilter; // <--- NEW
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieMaxAge(SESSION_TIMEOUT_SECONDS);
        return serializer;
    }

    @Bean
    public CookieClearingLogoutHandler cookieClearingLogoutHandler() {
        return new CookieClearingLogoutHandler("JSESSIONID", "SESSION");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityContext(context -> context
                        .requireExplicitSave(false)
                )
                // NEW: Disable CSRF for the API endpoints so the C# App can POST later
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/recon/**")
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/index", "/", "/favicon.ico",
                                "/styles/**", "/images/**",
                                "/login", "/perform_logout", "/error/**",
                                "/champions", "/champions/",
                                "/clanmembers", "/clanmembers/",
                                "/api/recon/champions",
                                "/api/recon/library",
                                "/api/app/**"
                        ).permitAll()
                        .requestMatchers("/admin/masquerade").authenticated()
                        .requestMatchers("/profile", "/champions/**", "/clanmembers/**").hasRole("USER")
                        .anyRequest().hasRole("USER")
                )
                // ADDED: Our new Token Filter runs before the standard Authorization check
                .addFilterBefore(appTokenFilter, AuthorizationFilter.class)
                .addFilterBefore(sessionSecurityFilter, AuthorizationFilter.class)
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.sendRedirect("/error/403");
                        })
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getUserPrincipal() != null) {
                                response.sendRedirect("/error/403");
                            } else {
                                response.sendRedirect("/login");
                            }
                        })
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(new SavedRequestAwareAuthenticationSuccessHandler())
                        .failureHandler(failureHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/perform_logout")
                        .invalidateHttpSession(true)
                        .addLogoutHandler(cookieClearingLogoutHandler())
                        .logoutSuccessUrl("/").permitAll()
                );

        return http.build();
    }
}