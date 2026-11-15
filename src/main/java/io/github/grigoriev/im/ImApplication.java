package io.github.grigoriev.im;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point of the Invoice Manager. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ImApplication {

    /**
     * Starts the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ImApplication.class, args);
    }
}
