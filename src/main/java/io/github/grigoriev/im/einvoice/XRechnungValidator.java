package io.github.grigoriev.im.einvoice;

import de.kosit.validationtool.api.Check;
import de.kosit.validationtool.api.Configuration;
import de.kosit.validationtool.api.InputFactory;
import de.kosit.validationtool.api.Result;
import de.kosit.validationtool.impl.DefaultCheck;
import de.kosit.validationtool.impl.xml.ProcessorProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Validates XRechnung XML with the KoSIT validator and the official XRechnung configuration. */
@Component
public class XRechnungValidator {

    static final String NAME = "KoSIT";

    private final Path configuration;
    private Check check;

    /**
     * Creates the validator.
     *
     * @param configuration directory of the unpacked XRechnung configuration, holding {@code scenarios.xml}
     */
    public XRechnungValidator(@Value("${im.kosit.xrechnung-dir:target/kosit-xrechnung}") Path configuration) {
        this.configuration = configuration;
    }

    /**
     * Validates the XML.
     *
     * @param xml the XRechnung XML
     * @return the result with the processing errors, schema violations and failed rules
     */
    public synchronized ValidationResult validate(byte[] xml) {
        Result result = check().checkInput(InputFactory.read(xml, "invoice.xml"));
        if (result.isAcceptable()) {
            return new ValidationResult(true, NAME, List.of());
        }
        // KoSIT returns null instead of an empty list for the stages it did not reach.
        List<String> messages = new ArrayList<>(orEmpty(result.getProcessingErrors()));
        orEmpty(result.getSchemaViolations()).forEach(violation -> messages.add(violation.getMessage()));
        orEmpty(result.getFailedAsserts())
                .forEach(failed -> messages.add(failed.getId() + ": " + text(failed.getText().getContent())));
        return new ValidationResult(false, NAME, messages);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String text(List<?> content) {
        StringBuilder text = new StringBuilder();
        content.forEach(text::append);
        return text.toString().strip();
    }

    private Check check() {
        if (check == null) {
            Path scenarios = configuration.resolve("scenarios.xml");
            if (!Files.isRegularFile(scenarios)) {
                throw new EInvoiceException("XRechnung configuration not found: " + scenarios.toAbsolutePath());
            }
            Configuration config = Configuration.load(scenarios.toUri(), configuration.toUri())
                    .build(ProcessorProvider.getProcessor());
            check = new DefaultCheck(config);
        }
        return check;
    }
}
