package io.github.grigoriev.im;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.grigoriev.im.einvoice.TaxCategory;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import io.github.grigoriev.im.seller.SellerTestData;
import io.github.grigoriev.im.seller.TaxNoteService;
import io.github.grigoriev.im.seller.TaxStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
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
        SellerProfile first = SellerTestData.save(sellers, SellerTestData.profile());
        assertThat(sellers.find()).contains(first);

        SellerProfile p = SellerTestData.profile();
        SellerTestData.save(sellers, new SellerProfile(p.name(), "Neuer Weg 2", p.postcode(), p.city(), p.countryCode(), "Max Mustermann",
                p.email(), p.phone(), null, "DE123456789", p.iban(), p.bic(), TaxStatus.REGULAR, null, null, null, null));
        SellerProfile stored = sellers.find().orElseThrow();
        assertThat(stored.street()).isEqualTo("Neuer Weg 2");
        assertThat(stored.contactName()).isEqualTo("Max Mustermann");
        assertThat(stored.taxNumber()).isNull();
        assertThat(stored.vatId()).isEqualTo("DE123456789");
        assertThat(stored.taxStatus()).isEqualTo(TaxStatus.REGULAR);
        assertThat(stored.tagline()).isNull();
        assertThat(SellerTestData.save(sellers, SellerTestData.profile()).bankName()).isEqualTo("Commerzbank");
        SellerProfile withDetails = sellers.find().orElseThrow();
        assertThat(withDetails.tagline()).isEqualTo("Freiberufliche Dozentin");
        assertThat(withDetails.website()).isEqualTo("www.erika.example");
        assertThat(withDetails.accountHolder()).isNull();
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
    void parallelFirstSavesNeverCollide() throws Exception {
        jdbc.update("DELETE FROM seller_profile");
        Map<TaxCategory, String> note = Map.of(TaxCategory.REVERSE_CHARGE_EU, "Reverse Charge, § 13b UStG.");
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Object>> saves = IntStream.range(0, 16).<Callable<Object>>mapToObj(n -> n % 2 == 0
                    ? () -> SellerTestData.save(sellers, SellerTestData.profile())
                    : () -> {
                        notes.save(note);
                        return null;
                    }).toList();
            for (Future<Object> save : pool.invokeAll(saves)) {
                save.get();
            }
        }
        assertThat(sellers.find()).contains(SellerTestData.profile());
        assertThat(notes.note(TaxCategory.REVERSE_CHARGE_EU)).isEqualTo("Reverse Charge, § 13b UStG.");
        notes.save(Map.of(TaxCategory.REVERSE_CHARGE_EU, ""));
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
