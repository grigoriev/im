package io.github.grigoriev.im.seller;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
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
    static final Pattern PHONE = Pattern.compile("\\+?[\\d ()/-]{5,49}");
    static final Pattern VAT_ID = Pattern.compile("[A-Z]{2}[A-Z0-9]{2,12}");
    static final Pattern IBAN = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}");

    /** The IBAN length of the SEPA countries (ISO 13616 registry). Other countries only need 15 to 34 characters. */
    static final Map<String, Integer> IBAN_LENGTHS = Map.ofEntries(
            Map.entry("AD", 24), Map.entry("AT", 20), Map.entry("BE", 16), Map.entry("BG", 22), Map.entry("CH", 21),
            Map.entry("CY", 28), Map.entry("CZ", 24), Map.entry("DE", 22), Map.entry("DK", 18), Map.entry("EE", 20),
            Map.entry("ES", 24), Map.entry("FI", 18), Map.entry("FR", 27), Map.entry("GB", 22), Map.entry("GI", 23),
            Map.entry("GR", 27), Map.entry("HR", 21), Map.entry("HU", 28), Map.entry("IE", 22), Map.entry("IS", 26),
            Map.entry("IT", 27), Map.entry("LI", 21), Map.entry("LT", 20), Map.entry("LU", 20), Map.entry("LV", 21),
            Map.entry("MC", 27), Map.entry("MT", 31), Map.entry("NL", 18), Map.entry("NO", 15), Map.entry("PL", 28),
            Map.entry("PT", 25), Map.entry("RO", 24), Map.entry("SE", 24), Map.entry("SI", 19), Map.entry("SK", 24),
            Map.entry("SM", 27), Map.entry("VA", 22));

    /** Inserts the one row or replaces it, atomic even if two users save the first profile at once. */
    static final String UPSERT = """
            INSERT INTO seller_profile (id, name, street, postcode, city, country_code, contact_name, email, phone,
                                        tax_number, vat_id, iban, bic, tax_status)
            VALUES (1, :name, :street, :postcode, :city, :countryCode, :contactName, :email, :phone,
                    :taxNumber, :vatId, :iban, :bic, :taxStatus)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, street = EXCLUDED.street,
                postcode = EXCLUDED.postcode, city = EXCLUDED.city, country_code = EXCLUDED.country_code,
                contact_name = EXCLUDED.contact_name, email = EXCLUDED.email, phone = EXCLUDED.phone,
                tax_number = EXCLUDED.tax_number, vat_id = EXCLUDED.vat_id, iban = EXCLUDED.iban,
                bic = EXCLUDED.bic, tax_status = EXCLUDED.tax_status""";
    static final Pattern BIC = Pattern.compile("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?");

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private final StoredProfileRepository profiles;
    private final JdbcClient jdbc;

    /**
     * Creates the service.
     *
     * @param profiles reads the profile
     * @param jdbc writes the profile
     */
    public SellerService(StoredProfileRepository profiles, JdbcClient jdbc) {
        this.profiles = profiles;
        this.jdbc = jdbc;
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
        jdbc.sql(UPSERT)
                .param("name", profile.name()).param("street", profile.street())
                .param("postcode", profile.postcode()).param("city", profile.city())
                .param("countryCode", profile.countryCode()).param("contactName", profile.contactName())
                .param("email", profile.email()).param("phone", profile.phone())
                .param("taxNumber", profile.taxNumber()).param("vatId", profile.vatId())
                .param("iban", profile.iban()).param("bic", profile.bic())
                .param("taxStatus", profile.taxStatus().name())
                .update();
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
        optional(problems, profile.email(), "E-Mail", 254);
        matches(problems, profile.phone(), PHONE, "Bitte eine Telefonnummer angeben, zum Beispiel +49 89 1234567.");
        optional(problems, profile.taxNumber(), "Steuernummer", 32);
        if (profile.vatId() != null && !VAT_ID.matcher(profile.vatId()).matches()) {
            problems.add("Die USt-IdNr. beginnt mit dem Ländercode, zum Beispiel DE123456789.");
        }
        if (profile.taxNumber() == null && profile.vatId() == null) {
            problems.add("Bitte die Steuernummer oder die USt-IdNr. angeben.");
        }
        checkIban(problems, profile.iban());
        matches(problems, profile.bic(), BIC, "Die BIC hat 8 oder 11 Zeichen.");
        if (profile.taxStatus() == null) {
            problems.add("Bitte den Steuerstatus wählen.");
        }
        return problems;
    }

    private static void checkIban(List<String> problems, String iban) {
        if (iban == null || !IBAN.matcher(iban).matches() || !checksumOk(iban)) {
            problems.add("Die IBAN ist ungültig.");
            return;
        }
        String country = iban.substring(0, 2);
        Integer length = IBAN_LENGTHS.get(country);
        if (length != null && iban.length() != length) {
            problems.add("Eine IBAN aus " + country + " hat " + length + " Zeichen.");
        }
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
