package com.party.security.config;


import com.party.security.jwt.*;
import com.party.security.services.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.*;
import java.util.List;

import static jakarta.servlet.DispatcherType.ERROR;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfiguration {

    private final UserDetailsServiceImpl userDetailsService;
    private final AuthEntryPointJwt unauthorizedHandler;


    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter(JwtUtils jwtUtils, AuthCookieService cookies) {
        return new AuthTokenFilter(jwtUtils, userDetailsService, unauthorizedHandler, cookies);
    }

    @Bean
    public FilterRegistrationBean<AuthTokenFilter> jwtFilterRegistration(AuthTokenFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Only execute inside Spring Security's chain.
        return registration;
    }

    // to retrieve details from userDetailsService
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider =
                new DaoAuthenticationProvider(userDetailsService);

        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration builder) throws Exception {
        return builder.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthTokenFilter jwtFilter, CookieCsrfTokenRepository csrfTokens, CorsConfigurationSource corsConfigurationSource) throws Exception {

        // JWT authentication restores the account on every request; it is not a new login.
        // Rotate CSRF explicitly on signin/signout in AuthenticationController instead of
        // letting ordinary authenticated reads invalidate the browser's cached token.
        // Default XOR/BREACH protection and CSRF validation on writes remain enabled.
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()));
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        http.logout(logout -> logout.disable());
        http.formLogin(form -> form.disable()).httpBasic(basic -> basic.disable());
        http.requestCache(cache -> cache.disable());
        //this is for JWT
        http.exceptionHandling(exception->exception
                .authenticationEntryPoint(unauthorizedHandler)
                .accessDeniedHandler(new ApiAccessDeniedHandler()));
        //this is for the stateless behavior
        http.sessionManagement(session->session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        String[] areaAuthorities = {"PARTY_HEAD", "STATE_ADMIN", "DIVISION_ADMIN", "DISTRICT_ADMIN",
                "DIVISIONAL_DISTRICT_ADMIN", "CONSTITUENCY_ADMIN", "TALUKA_ADMIN",
                "MUNICIPAL_CORPORATION_ADMIN", "MUNICIPAL_COUNCIL_ADMIN", "NAGAR_PANCHAYAT_ADMIN"};
        http.authorizeHttpRequests(request->request
                .dispatcherTypeMatchers(ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/portal/summary",
                        "/api/public/portal/state-executive",
                        "/api/public/portal/state-executive/positions",
                        "/api/public/portal/organization-units").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/signin",
                        "/api/auth/signup",
                        "/api/auth/signout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/assembly-constituencies",
                        "/api/assembly-constituencies/*").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/admin/dashboard", "/api/admin/registration-applications",
                        "/api/admin/registration-applications/*", "/api/admin/registration-applications/*/history",
                        "/api/advocate-assignments", "/api/advocate-assignments/**").hasAnyAuthority(areaAuthorities)
                .requestMatchers("/api/admin/**",
                        "/api/advocate-assignments/**",
                        "/api/assembly-constituencies/**").hasAuthority("PARTY_HEAD")
                .requestMatchers(HttpMethod.GET,
                        "/api/organization-units/**",
                        "/api/committees/**",
                        "/api/committee-positions/**").authenticated()
                .requestMatchers("/api/organization-units/**",
                        "/api/committees/**",
                        "/api/committee-positions/**").hasAnyAuthority(areaAuthorities)
                .anyRequest().authenticated());

        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${app.security.cookie.secure:true}") boolean secure,
            @Value("${app.security.cookie.same-site:Lax}") String sameSite) {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(secure)
                .sameSite(sameSite).path("/api"));
        return repository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins}") List<String> origins) {
        if (origins.stream().anyMatch(origin -> origin.contains("*")))
            throw new IllegalArgumentException("Use exact allowed CORS origins, without wildcards");
        var config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

}
