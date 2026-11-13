package io.github.grigoriev.im.country;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The countries of the forms. Java knows every ISO 3166 country with its German name. The admin keeps the
 * short list that the forms offer, table {@code country}.
 */
@Service
public class CountryService {

    /** Every ISO 3166-1 alpha-2 code. */
    static final Set<String> CODES = Set.of(Locale.getISOCountries());

    static final String LIST = "SELECT code FROM country";
    static final String ADD = "INSERT INTO country (code) VALUES (:code) ON CONFLICT (code) DO NOTHING";
    static final String REMOVE = "DELETE FROM country WHERE code = :code";

    private static final Comparator<Country> BY_NAME =
            Comparator.comparing(Country::name, Collator.getInstance(Locale.GERMAN));

    private final JdbcClient jdbc;

    /**
     * Creates the service.
     *
     * @param jdbc the database
     */
    public CountryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tells whether a code is an ISO 3166 country.
     *
     * @param code the code, may be {@code null}
     * @return {@code true} for a known country
     */
    public static boolean isKnown(String code) {
        return code != null && CODES.contains(code);
    }

    /**
     * Returns the German name of a country.
     *
     * @param code the ISO code
     * @return the name, or the code itself if it is unknown
     */
    public static String name(String code) {
        return isKnown(code) ? Locale.of("", code).getDisplayCountry(Locale.GERMAN) : String.valueOf(code);
    }

    /**
     * Returns the short list of the admin, by German name.
     *
     * @return the countries
     */
    public List<Country> enabled() {
        return sorted(codes().stream());
    }

    /**
     * Returns the countries of a form: the short list, plus the codes to keep even if the admin removed them,
     * such as the stored country of the record or the country of a form that comes back with a problem. A code
     * that is no ISO country shows as it is, so saving never changes it unseen: the check then asks for a
     * country from the list.
     *
     * @param keep the codes to keep; {@code null} and blank entries are ignored
     * @return the countries by German name
     */
    public List<Country> options(String... keep) {
        List<String> codes = codes();
        Stream<String> extra = Stream.of(keep)
                .filter(code -> code != null && !code.isBlank() && !codes.contains(code)).distinct();
        return sorted(Stream.concat(codes.stream(), extra));
    }

    /**
     * Returns every country that is not on the short list, for the admin to add.
     *
     * @return the countries by German name
     */
    public List<Country> others() {
        List<String> codes = codes();
        return sorted(CODES.stream().filter(code -> !codes.contains(code)));
    }

    /**
     * Adds a country to the short list.
     *
     * @param code the ISO code
     * @return the country
     * @throws IllegalArgumentException if the code is not an ISO country
     */
    @Transactional
    public Country add(String code) {
        if (!isKnown(code)) {
            throw new IllegalArgumentException("Unknown country " + code);
        }
        jdbc.sql(ADD).param("code", code).update();
        return new Country(code, name(code));
    }

    /**
     * Removes a country from the short list. Records with that country keep it.
     *
     * @param code the ISO code
     * @return the country
     */
    @Transactional
    public Country remove(String code) {
        jdbc.sql(REMOVE).param("code", code).update();
        return new Country(code, name(code));
    }

    private List<String> codes() {
        return jdbc.sql(LIST).query(String.class).list();
    }

    private static List<Country> sorted(Stream<String> codes) {
        return codes.map(code -> new Country(code, name(code))).sorted(BY_NAME).toList();
    }
}
