package io.github.grigoriev.im.seller;

import static io.github.grigoriev.im.validation.Fields.compact;
import static io.github.grigoriev.im.validation.Fields.checkCountry;
import static io.github.grigoriev.im.validation.Fields.checkEmail;
import static io.github.grigoriev.im.validation.Fields.matches;
import static io.github.grigoriev.im.validation.Fields.optional;
import static io.github.grigoriev.im.validation.Fields.text;
import static io.github.grigoriev.im.validation.Fields.upper;
import static io.github.grigoriev.im.validation.Fields.checkVatId;

import io.github.grigoriev.im.audit.AuditAction;
import io.github.grigoriev.im.audit.AuditService;
import java.math.BigInteger;
import java.net.IDN;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and saves the seller profile. It saves an incomplete profile and refuses only wrong values, such as an IBAN
 * with a wrong checksum. {@link #missing(SellerProfile)} names what an invoice still needs from the seller: the
 * address, the contact for XRechnung (BR-DE-2), a tax number or VAT ID (§14 Abs. 4 Nr. 2 UStG) and the bank account.
 */
@Service
public class SellerService {

    /** The ID of the one profile row. */
    static final int ID = 1;

    static final Pattern PHONE = Pattern.compile("\\+?[\\d ()/-]{5,49}");
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
                                        tax_number, vat_id, iban, bic, tax_status, tagline, website, bank_name,
                                        account_holder)
            VALUES (1, :name, :street, :postcode, :city, :countryCode, :contactName, :email, :phone,
                    :taxNumber, :vatId, :iban, :bic, :taxStatus, :tagline, :website, :bankName, :accountHolder)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, street = EXCLUDED.street,
                postcode = EXCLUDED.postcode, city = EXCLUDED.city, country_code = EXCLUDED.country_code,
                contact_name = EXCLUDED.contact_name, email = EXCLUDED.email, phone = EXCLUDED.phone,
                tax_number = EXCLUDED.tax_number, vat_id = EXCLUDED.vat_id, iban = EXCLUDED.iban,
                bic = EXCLUDED.bic, tax_status = EXCLUDED.tax_status, tagline = EXCLUDED.tagline,
                website = EXCLUDED.website, bank_name = EXCLUDED.bank_name, account_holder = EXCLUDED.account_holder""";
    /** The scheme of a website, in any case. */
    static final Pattern SCHEME = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

    /** A host in ASCII, after {@link IDN#toASCII(String)}: letters, digits, dots and hyphens. */
    static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]+");

    static final Pattern BIC = Pattern.compile("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?");

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private final StoredProfileRepository profiles;
    private final JdbcClient jdbc;
    private final AuditService audit;

    /**
     * Creates the service.
     *
     * @param profiles reads the profile
     * @param jdbc writes the profile
     * @param audit the audit log
     */
    public SellerService(StoredProfileRepository profiles, JdbcClient jdbc, AuditService audit) {
        this.profiles = profiles;
        this.jdbc = jdbc;
        this.audit = audit;
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
     * Checks, normalizes and saves one part of the profile; the other parts stay as stored. Empty fields are allowed:
     * the issue of an invoice asks for them.
     *
     * @param part the tab that was saved
     * @param input the form of that tab
     * @return the whole saved profile, for example with the IBAN without spaces
     * @throws SettingsException with the wrong values, by field
     */
    @Transactional
    public SellerProfile save(ProfilePart part, SellerProfile input) {
        SellerProfile stored = profiles.findById(ID).map(StoredProfile::profile).orElseGet(SellerProfile::empty);
        SellerProfile profile = normalize(part.merge(stored, input));
        Map<String, String> problems = check(profile);
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
                .param("taxStatus", profile.taxStatus() == null ? null : profile.taxStatus().name())
                .param("tagline", profile.tagline()).param("website", profile.website())
                .param("bankName", profile.bankName()).param("accountHolder", profile.accountHolder())
                .update();
        audit.add(AuditAction.PROFILE_SAVED, profile.name() == null ? "" : profile.name());
        return profile;
    }

    /**
     * Names the fields every invoice needs that the profile leaves empty. XRechnung needs more, see
     * {@link #missingForXRechnung(SellerProfile)}.
     *
     * @param input the profile, as entered or stored
     * @return the German label of each empty field, by field name, in the order of the form
     */
    public static Map<String, String> missing(SellerProfile input) {
        SellerProfile profile = normalize(input);
        Map<String, String> missing = new LinkedHashMap<>();
        need(missing, profile.name(), "name", "Name");
        need(missing, profile.street(), "street", "Straße");
        need(missing, profile.postcode(), "postcode", "PLZ");
        need(missing, profile.city(), "city", "Ort");
        need(missing, profile.countryCode(), "countryCode", "Land");
        need(missing, profile.taxStatus(), "taxStatus", "Steuerstatus");
        need(missing, profile.taxNumber() == null ? profile.vatId() : profile.taxNumber(), "taxNumber",
                "Steuernummer oder USt-IdNr.");
        need(missing, profile.iban(), "iban", "IBAN");
        need(missing, profile.bic(), "bic", "BIC");
        return missing;
    }

    /**
     * Names the contact fields XRechnung needs from the seller (BR-DE-6, BR-DE-7) that the profile leaves empty.
     *
     * @param input the profile, as entered or stored
     * @return the German label of each empty field, by field name
     */
    public static Map<String, String> missingForXRechnung(SellerProfile input) {
        SellerProfile profile = normalize(input);
        Map<String, String> missing = new LinkedHashMap<>();
        need(missing, profile.email(), "email", "E-Mail");
        need(missing, profile.phone(), "phone", "Telefon");
        return missing;
    }

    private static void need(Map<String, String> missing, Object value, String field, String label) {
        if (value == null) {
            missing.put(field, label);
        }
    }

    static SellerProfile normalize(SellerProfile input) {
        return new SellerProfile(text(input.name()), text(input.street()), text(input.postcode()), text(input.city()),
                upper(input.countryCode()), text(input.contactName()), text(input.email()), text(input.phone()),
                text(input.taxNumber()), compact(input.vatId()), compact(input.iban()), compact(input.bic()),
                input.taxStatus(), text(input.tagline()), text(input.website()), text(input.bankName()),
                text(input.accountHolder()));
    }

    /**
     * Checks the entered values. An empty field is no problem here, see {@link #missing(SellerProfile)}.
     *
     * @return the first problem of each wrong field, by field name, in the order of the form
     */
    static Map<String, String> check(SellerProfile profile) {
        Map<String, String> problems = new LinkedHashMap<>();
        field(problems, "name", found -> optional(found, profile.name(), "Name", 200));
        field(problems, "tagline", found -> optional(found, profile.tagline(), "Zusatz", 200));
        field(problems, "street", found -> optional(found, profile.street(), "Straße", 200));
        field(problems, "postcode", found -> optional(found, profile.postcode(), "PLZ", 16));
        field(problems, "city", found -> optional(found, profile.city(), "Ort", 100));
        given(problems, "countryCode", profile.countryCode(), found -> checkCountry(found, profile.countryCode()));
        field(problems, "contactName", found -> optional(found, profile.contactName(), "Ansprechpartner", 200));
        given(problems, "email", profile.email(), found -> checkEmail(found, profile.email()));
        given(problems, "phone", profile.phone(), found -> matches(found, profile.phone(), PHONE,
                "Bitte eine Telefonnummer angeben, zum Beispiel +49 89 1234567."));
        given(problems, "website", profile.website(), found -> {
            optional(found, profile.website(), "Website", 200);
            if (!websiteOk(profile.website())) {
                found.add("Bitte eine Website wie www.example.de angeben.");
            }
        });
        field(problems, "taxNumber", found -> optional(found, profile.taxNumber(), "Steuernummer", 32));
        field(problems, "vatId", found -> checkVatId(found, profile.vatId()));
        given(problems, "iban", profile.iban(), found -> checkIban(found, profile.iban()));
        given(problems, "bic", profile.bic(), found -> matches(found, profile.bic(), BIC,
                "Die BIC hat 8 oder 11 Zeichen."));
        field(problems, "bankName", found -> optional(found, profile.bankName(), "Kreditinstitut", 100));
        field(problems, "accountHolder", found -> optional(found, profile.accountHolder(), "Kontoinhaber", 200));
        return problems;
    }

    /** Runs the check of a field and keeps its first problem. */
    private static void field(Map<String, String> problems, String field, Consumer<List<String>> check) {
        List<String> found = new ArrayList<>();
        check.accept(found);
        if (!found.isEmpty()) {
            problems.put(field, found.getFirst());
        }
    }

    /** Runs the check of a field only if it has a value. */
    private static void given(Map<String, String> problems, String field, String value, Consumer<List<String>> check) {
        if (value != null) {
            field(problems, field, check);
        }
    }

    private static void checkIban(List<String> problems, String iban) {
        if (!IBAN.matcher(iban).matches() || !checksumOk(iban)) {
            problems.add("Die IBAN ist ungültig.");
            return;
        }
        String country = iban.substring(0, 2);
        Integer length = IBAN_LENGTHS.get(country);
        if (length != null && iban.length() != length) {
            problems.add("Eine IBAN aus " + country + " hat " + length + " Zeichen.");
        }
    }

    /**
     * A website needs a host with a dot inside, such as {@code www.example.de} or {@code www.müller.de}, no empty
     * part and no space. The scheme {@code http://} or {@code https://} is optional.
     */
    static boolean websiteOk(String website) {
        String[] parts = SCHEME.matcher(website).replaceFirst("").split("/", 2);
        if (website.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        String host;
        try {
            host = IDN.toASCII(parts[0]);
        } catch (IllegalArgumentException _) {
            return false;
        }
        // IDN.toASCII already refuses empty labels such as ".example.de" or "www..example.de".
        return HOST.matcher(host).matches() && host.contains(".") && !host.endsWith(".");
    }

    /** ISO 13616: the IBAN with its first four characters moved to the end, letters as numbers, mod 97 is 1. */
    static boolean checksumOk(String iban) {
        String rotated = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        rotated.chars().forEach(c -> digits.append(Character.getNumericValue(c)));
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).equals(BigInteger.ONE);
    }
}
