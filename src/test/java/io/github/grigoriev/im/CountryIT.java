package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.grigoriev.im.country.Country;
import io.github.grigoriev.im.country.CountryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The short list of countries against PostgreSQL. */
@SpringBootTest
@Import(TestDatabase.class)
class CountryIT {

    @Autowired
    private CountryService countries;

    @Test
    void startsWithGermanyOnlyAndKeepsTheChanges() {
        assertThat(countries.enabled()).extracting(Country::code).containsExactly("DE");
        countries.add("JP");
        countries.add("JP");
        countries.add("AT");
        assertThat(countries.enabled()).extracting(Country::code).containsExactly("DE", "JP", "AT");
        assertThat(countries.others()).extracting(Country::code).doesNotContain("JP", "AT");
        countries.remove("JP");
        countries.remove("AT");
        assertThat(countries.enabled()).extracting(Country::code).containsExactly("DE");
        assertThat(countries.options("JP")).extracting(Country::code).contains("JP");
    }
}
