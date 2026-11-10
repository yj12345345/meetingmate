package com.meetingmate.app.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AuthConfig {

    /** Injected so token expiry / grace-window logic can be tested with a fixed clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
