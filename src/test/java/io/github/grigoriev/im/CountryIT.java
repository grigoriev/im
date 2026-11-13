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
    void startsWithTheEuAndItsNeighborsAndKeepsTheChanges() {
        assertThat(countries.enabled()).extracting(Country::code).contains("DE", "AT", "FR", "CH", "GB", "US")
                .doesNotContain("JP").hasSize(32);
        countries.add("JP");
        countries.add("JP");
        assertThat(countries.enabled()).extracting(Country::code).contains("JP").hasSize(33);
        assertThat(countries.others()).extracting(Country::code).doesNotContain("JP");
        countries.remove("JP");
        assertThat(countries.enabled()).hasSize(32);
        assertThat(countries.options("JP")).extracting(Country::code).contains("JP");
    }
}
