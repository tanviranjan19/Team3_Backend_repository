package com.example.bankbff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

/**
 * BFF security configuration.
 *
 *  - Require authentication for /api/** endpoints.
 *  - Allow /oauth2/** and /login/** unauthenticated so the OAuth flow can complete.
 *  - Return 401 for JSON requests, redirect to login for browser navigation.
 *  - CSRF protection enabled with cookie-based token repository for SPA.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // TODO 4.1: Configure authorizeHttpRequests so that:
                //   - "/oauth2/**" and "/login/**" are permitted without auth
                //     (the OAuth flow needs to be able to reach these endpoints
                //     while the user is still anonymous)
                //   - "/api/**" requires authentication
                //   - any other request requires authentication
                //
                // Example structure:
                // .authorizeHttpRequests(auth -> auth
                //         .requestMatchers("/oauth2/**", "/login/**").permitAll()
                //         .requestMatchers("/api/**").authenticated()
                //         .anyRequest().authenticated())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())

                // TODO 4.2: Configure exceptionHandling so that the BFF responds
                // differently depending on whether the caller is a browser or a JSON client.
                //
                // Use defaultAuthenticationEntryPointFor with a MediaTypeRequestMatcher
                // for MediaType.APPLICATION_JSON. The entry point for JSON requests is
                // new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED). The default entry
                // point (used for non-JSON requests) is a LoginUrlAuthenticationEntryPoint
                // pointed at "/oauth2/authorization/bank-auth".
                //
                // Example structure:
                // .exceptionHandling(ex -> ex
                //         .defaultAuthenticationEntryPointFor(
                //                 new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                //                 new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON))
                //         .authenticationEntryPoint(
                //                 new LoginUrlAuthenticationEntryPoint(
                //                         "/oauth2/authorization/bank-auth")))

                .exceptionHandling(ex -> {
                    // Match an explicit "application/json" Accept header, not a browser's "*/*".
                    MediaTypeRequestMatcher jsonMatcher =
                            new MediaTypeRequestMatcher(MediaType.APPLICATION_JSON);
                    jsonMatcher.setUseEquals(true);
                    ex
                            // JSON clients (the SPA's fetch) get a clean 401.
                            .defaultAuthenticationEntryPointFor(
                                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                    jsonMatcher)
                            // Everything else (a browser navigating to a protected URL) is
                            // redirected into the OAuth login flow.
                            .defaultAuthenticationEntryPointFor(
                                    new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/bank-auth"),
                                    AnyRequestMatcher.INSTANCE);
                })

                // Activate the OAuth2 login flow.
                // This is what makes /oauth2/authorization/{regId} actually do the redirect.
                .oauth2Login(Customizer.withDefaults())

                // Logout configuration. Sending POST /logout invalidates the session.
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true))

                // Enable CSRF protection with a cookie-based token repository.
                // The CSRF token is stored in a cookie that the SPA can read and
                // must include in the X-CSRF-TOKEN header for state-changing requests.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Don't require CSRF token for the OAuth2/login flow itself
                        .ignoringRequestMatchers("/oauth2/**", "/login/**"));

        return http.build();
    }
}
