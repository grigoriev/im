package io.github.grigoriev.im.seller;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and saves the seller profile. It checks what an invoice needs from the seller: the address,
 * the contact for XRechnung (BR-DE-2), a tax number or VAT ID (§14 Abs. 4 Nr. 2 UStG) and a valid IBAN.
 */
@Service
public class SellerService {

    /** The ID of the one profile row. */
    static final int ID = 1;

    static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");
    static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    static final Pattern PHONE = Pattern.compile("\\+?[0-9 ()/-]{5,50}");
    static final Pattern VAT_ID = Pattern.compile("[A-Z]{2}[A-Z0-9]{2,12}");
    static final Pattern IBAN = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");
    static final Pattern BIC = Pattern.compile("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?");

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private final StoredProfileRepository profiles;
    private final JdbcAggregateOperations aggregates;

    /**
     * Creates the service.
     *
     * @param profiles reads the profile
     * @param aggregates inserts and updates the profile
     */
    public SellerService(StoredProfileRepository profiles, JdbcAggregateOperations aggregates) {
        this.profiles = profiles;
        this.aggregates = aggregates;
    }

    /**
     * Returns the profile.
     *
     * @return the profile, empty before the first save
     */
    public Optional<SellerProfile> find() {
        return profiles.findById(ID).map(StoredProfile::profile);
    }

    /**
     * Checks, normalizes and saves the profile.
     *
     * @param input the profile as entered
     * @return the saved profile, for example with the IBAN without spaces
     * @throws SettingsException with every problem found
     */
    @Transactional
    public SellerProfile save(SellerProfile input) {
        SellerProfile profile = normalize(input);
        List<String> problems = check(profile);
        if (!problems.isEmpty()) {
            throw new SettingsException(problems);
        }
        StoredProfile row = new StoredProfile(ID, profile);
        if (profiles.existsById(ID)) {
            aggregates.update(row);
        } else {
            aggregates.insert(row);
        }
        return profile;
    }

    static SellerProfile normalize(SellerProfile input) {
        return new SellerProfile(text(input.name()), text(input.street()), text(input.postcode()), text(input.city()),
                upper(input.countryCode()), text(input.contactName()), text(input.email()), text(input.phone()),
                text(input.taxNumber()), compact(input.vatId()), compact(input.iban()), compact(input.bic()),
                input.taxStatus());
    }

    static List<String> check(SellerProfile profile) {
        List<String> problems = new ArrayList<>();
        required(problems, profile.name(), "Name", 200);
        required(problems, profile.street(), "Straße", 200);
        required(problems, profile.postcode(), "PLZ", 16);
        required(problems, profile.city(), "Ort", 100);
        optional(problems, profile.contactName(), "Ansprechpartner", 200);
        matches(problems, profile.countryCode(), COUNTRY, "Das Land ist ein Code aus zwei Buchstaben, zum Beispiel DE.");
        matches(problems, profile.email(), EMAIL, "Bitte eine gültige E-Mail-Adresse angeben.");
        matches(problems, profile.phone(), PHONE, "Bitte eine Telefonnummer angeben, zum Beispiel +49 89 1234567.");
        optional(problems, profile.taxNumber(), "Steuernummer", 32);
        if (profile.vatId() != null && !VAT_ID.matcher(profile.vatId()).matches()) {
            problems.add("Die USt-IdNr. beginnt mit dem Ländercode, zum Beispiel DE123456789.");
        }
        if (profile.taxNumber() == null && profile.vatId() == null) {
            problems.add("Bitte die Steuernummer oder die USt-IdNr. angeben.");
        }
        if (profile.iban() == null || !IBAN.matcher(profile.iban()).matches() || !checksumOk(profile.iban())) {
            problems.add("Die IBAN ist ungültig.");
        }
        matches(problems, profile.bic(), BIC, "Die BIC hat 8 oder 11 Zeichen.");
        if (profile.taxStatus() == null) {
            problems.add("Bitte den Steuerstatus wählen.");
        }
        return problems;
    }

    /** ISO 13616: the IBAN with its first four characters moved to the end, letters as numbers, mod 97 is 1. */
    static boolean checksumOk(String iban) {
        String rotated = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        rotated.chars().forEach(c -> digits.append(Character.getNumericValue(c)));
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).equals(BigInteger.ONE);
    }

    private static void required(List<String> problems, String value, String field, int max) {
        if (value == null) {
            problems.add("Bitte " + field + " angeben.");
        } else {
            optional(problems, value, field, max);
        }
    }

    private static void optional(List<String> problems, String value, String field, int max) {
        if (value != null && value.length() > max) {
            problems.add(field + " hat höchstens " + max + " Zeichen.");
        }
    }

    private static void matches(List<String> problems, String value, Pattern pattern, String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            problems.add(message);
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String upper(String value) {
        String text = text(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private static String compact(String value) {
        String text = upper(value);
        return text == null ? null : text.replaceAll("\\s+", "");
    }
}
