package com.party.security.config;

import com.party.user.entity.AppUser;
import com.party.user.entity.RoleType;
import com.party.user.entity.Role;
import com.party.user.repository.RoleRepository;
import com.party.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@Configuration
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapConfiguration {
    @Bean
    CommandLineRunner bootstrapAdmin(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
            @Value("${app.bootstrap.username}") String username,
            @Value("${app.bootstrap.password}") String password,
            @Value("${app.bootstrap.mobile}") String mobile) {
        return args -> {
            String normalized = username.strip().toLowerCase(java.util.Locale.ROOT);
            if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,149}")
                    || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72
                    || !mobile.matches("\\+[1-9][0-9]{7,14}"))
                throw new IllegalArgumentException("Invalid bootstrap username, password or E.164 mobile");

            if (users.existsByUsername(normalized))
                return; // Never elevate an existing account.

            Role role = roles.findByName(RoleType.PARTY_HEAD).orElseGet(() -> {
                Role created = new Role();
                created.setName(RoleType.PARTY_HEAD);
                return roles.save(created);
            });

            users.saveAndFlush(
                    AppUser.builder()
                            .username(normalized)
                            .password(encoder.encode(password))
                    .fullName("Party Head Admin")
                            .mobile(mobile).enabled(true)
                            .passwordChangeRequired(false)
                    .roles(new HashSet<>(Set.of(role))).build());
        };
    }
}
