package io.github.grigoriev.im.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The clock of the app. Code takes the time from it, so tests can fix it. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** Time zone of the business: all dates of an invoice are German dates. */
    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /**
     * Returns the system clock in the German time zone.
     *
     * @return the clock
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
