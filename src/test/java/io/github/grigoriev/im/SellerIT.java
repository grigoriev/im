package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.seller.TaxNoteService;
import io.github.grigoriev.im.seller.TaxStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The seller profile and the tax notes against PostgreSQL. */
@SpringBootTest
@Import(TestDatabase.class)
class SellerIT {

    @Autowired
    private SellerService sellers;

    @Autowired
    private TaxNoteService notes;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void insertsTheProfileOnceAndUpdatesItThen() {
        SellerProfile first = sellers.save(SellerTestData.profile());
        assertThat(sellers.find()).contains(first);

        SellerProfile p = SellerTestData.profile();
        sellers.save(new SellerProfile(p.name(), "Neuer Weg 2", p.postcode(), p.city(), p.countryCode(), "Max Mustermann",
                p.email(), p.phone(), null, "DE123456789", p.iban(), p.bic(), TaxStatus.REGULAR));
        SellerProfile stored = sellers.find().orElseThrow();
        assertThat(stored.street()).isEqualTo("Neuer Weg 2");
        assertThat(stored.contactName()).isEqualTo("Max Mustermann");
        assertThat(stored.taxNumber()).isNull();
        assertThat(stored.vatId()).isEqualTo("DE123456789");
        assertThat(stored.taxStatus()).isEqualTo(TaxStatus.REGULAR);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seller_profile", Integer.class)).isEqualTo(1);
    }

    @Test
    void theDatabaseKeepsOneProfileWithATaxNumberOrVatId() {
        String insert = "INSERT INTO seller_profile (id, name, street, postcode, city, country_code, email, phone, iban, "
                + "bic, tax_status, tax_number) VALUES (?, 'n', 's', 'p', 'c', 'DE', 'e', 'p', 'i', 'b', 'REGULAR', ?)";
        assertThatThrownBy(() -> jdbc.update(insert, 2, "1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, 1, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesChangedTaxNotesAndFallsBackToTheDefault() {
        notes.save(Map.of(TaxCategory.EXEMPT_4_21, "Steuerfrei nach § 4 Nr. 21 a UStG."));
        assertThat(notes.note(TaxCategory.EXEMPT_4_21)).isEqualTo("Steuerfrei nach § 4 Nr. 21 a UStG.");
        assertThat(notes.notes()).containsEntry(TaxCategory.EXEMPT_4_21, "Steuerfrei nach § 4 Nr. 21 a UStG.")
                .containsEntry(TaxCategory.KLEINUNTERNEHMER, TaxCategory.KLEINUNTERNEHMER.defaultNote());

        notes.save(Map.of(TaxCategory.EXEMPT_4_21, "Noch einmal geändert."));
        assertThat(notes.note(TaxCategory.EXEMPT_4_21)).isEqualTo("Noch einmal geändert.");
        notes.save(Map.of(TaxCategory.EXEMPT_4_21, ""));
        assertThat(notes.note(TaxCategory.EXEMPT_4_21)).isEqualTo(TaxCategory.EXEMPT_4_21.defaultNote());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tax_note", Integer.class)).isZero();
    }
}
