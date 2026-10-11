package com.ganesh.aura.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemDefaultZone();
    }
}
