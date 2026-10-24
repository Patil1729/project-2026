package com.party.registration.escalation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ApprovalEscalationProperties.class)
public class ApprovalEscalationConfiguration {
    public static final String CLOCK_BEAN = "approvalEscalationClock";

    @Bean(name = CLOCK_BEAN)
    public Clock approvalEscalationClock() {
        return Clock.systemUTC();
    }
}
