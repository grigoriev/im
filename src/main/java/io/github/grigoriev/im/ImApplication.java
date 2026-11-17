package io.github.grigoriev.im;

import io.github.grigoriev.im.migration.Migration;
import java.util.Arrays;
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
     * Starts the application, or with the first argument {@code migrate} the migration of the database.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args.length > 0 && Migration.COMMAND.equals(args[0])) {
            Migration.run(Arrays.copyOfRange(args, 1, args.length));
        } else {
            SpringApplication.run(ImApplication.class, args);
        }
    }
}
