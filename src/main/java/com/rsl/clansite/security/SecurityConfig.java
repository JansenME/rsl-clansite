package com.rsl.clansite.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@EnableMongoHttpSession(maxInactiveIntervalInSeconds = 31536000)
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final int SESSION_TIMEOUT_SECONDS = 31536000;

    @Autowired
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService;

    private final CustomAuthenticationFailureHandler failureHandler;

    public SecurityConfig(CustomAuthenticationFailureHandler failureHandler) {
        this.failureHandler = failureHandler;
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/index", "/", "/styles/**", "/images/**", "/login**", "/perform_logout", "/login-error/**").permitAll()
                        .requestMatchers("/**").hasRole("OWNER")
                        .requestMatchers("/clanmembers/add", "/clanmembers/save").hasAnyRole("ADMIN", "COORDINATOR")
                        .requestMatchers("/champions/add", "/champions/save").hasRole("ADMIN")
                        .requestMatchers("/clanmembers/*/roster").hasAnyRole("ADMIN", "COORDINATOR")
                        .requestMatchers("/clanmembers", "/clanmembers/*", "/clanmembers/switch").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/oauth2/authorization/discord")
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
