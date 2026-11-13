package io.github.grigoriev.im;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of the Invoice Manager. */
@SpringBootApplication
@ConfigurationPropertiesScan
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
