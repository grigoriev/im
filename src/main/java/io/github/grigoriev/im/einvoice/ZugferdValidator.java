package io.github.grigoriev.im.einvoice;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mustangproject.validator.ZUGFeRDValidator;
import org.springframework.stereotype.Component;

/** Validates a ZUGFeRD PDF with the Mustang validator: PDF/A-3, EN 16931 and ZUGFeRD schematron. */
@Component
public class ZugferdValidator {

    static final String NAME = "Mustang";

    private static final Pattern ERROR = Pattern.compile("<error[^>]*>(.*?)</error>", Pattern.DOTALL);

    /**
     * Validates the PDF.
     *
     * @param pdf the ZUGFeRD PDF
     * @return the result with the error texts of the report
     */
    public ValidationResult validate(byte[] pdf) {
        ZUGFeRDValidator validator = new ZUGFeRDValidator();
        validator.disableNotices();
        String report = validator.validate(pdf, "invoice.pdf");
        boolean valid = validator.wasCompletelyValid();
        return new ValidationResult(valid, NAME, valid ? List.of() : errors(report));
    }

    static List<String> errors(String report) {
        List<String> errors = new ArrayList<>();
        Matcher matcher = ERROR.matcher(report);
        while (matcher.find()) {
            errors.add(matcher.group(1).strip());
        }
        if (errors.isEmpty()) {
            errors.add(report);
        }
        return errors;
    }
}
