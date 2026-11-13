package io.github.grigoriev.im.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.grigoriev.im.einvoice.TaxCategory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;

class TaxNoteServiceTest {

    private final TaxNoteRepository repository = mock(TaxNoteRepository.class);
    private final JdbcAggregateOperations aggregates = mock(JdbcAggregateOperations.class);
    private final TaxNoteService service = new TaxNoteService(repository, aggregates);

    @Test
    void coversTheExemptCategoriesOnly() {
        assertThat(TaxNoteService.CATEGORIES).containsExactly(TaxCategory.KLEINUNTERNEHMER, TaxCategory.EXEMPT_4_21,
                TaxCategory.REVERSE_CHARGE_EU, TaxCategory.NOT_TAXABLE_ABROAD);
    }

    @Test
    void returnsTheChangedNotesAndTheDefaultsForTheRest() {
        when(repository.findAll()).thenReturn(List.of(new TaxNote("EXEMPT_4_21", "Steuerfrei nach § 4 Nr. 21 a UStG.")));
        Map<TaxCategory, String> notes = service.notes();
        assertThat(notes).containsExactly(
                Map.entry(TaxCategory.KLEINUNTERNEHMER, TaxCategory.KLEINUNTERNEHMER.defaultNote()),
                Map.entry(TaxCategory.EXEMPT_4_21, "Steuerfrei nach § 4 Nr. 21 a UStG."),
                Map.entry(TaxCategory.REVERSE_CHARGE_EU, TaxCategory.REVERSE_CHARGE_EU.defaultNote()),
                Map.entry(TaxCategory.NOT_TAXABLE_ABROAD, TaxCategory.NOT_TAXABLE_ABROAD.defaultNote()));
    }

    @Test
    void returnsTheNoteOfOneCategory() {
        when(repository.findById("EXEMPT_4_21")).thenReturn(Optional.of(new TaxNote("EXEMPT_4_21", "Geändert.")));
        assertThat(service.note(TaxCategory.EXEMPT_4_21)).isEqualTo("Geändert.");
        assertThat(service.note(TaxCategory.KLEINUNTERNEHMER)).isEqualTo(TaxCategory.KLEINUNTERNEHMER.defaultNote());
        assertThat(service.note(TaxCategory.STANDARD_19)).isEmpty();
    }

    @Test
    void storesOnlyNotesThatDifferFromTheDefault() {
        Map<TaxCategory, String> changes = new HashMap<>();
        changes.put(TaxCategory.EXEMPT_4_21, "  Geändert.  ");
        changes.put(TaxCategory.KLEINUNTERNEHMER, TaxCategory.KLEINUNTERNEHMER.defaultNote());
        changes.put(TaxCategory.REVERSE_CHARGE_EU, " ");
        changes.put(TaxCategory.NOT_TAXABLE_ABROAD, null);
        changes.put(TaxCategory.STANDARD_19, "Ignoriert.");
        service.save(changes);
        verify(aggregates).insert(new TaxNote("EXEMPT_4_21", "Geändert."));
        verify(aggregates, never()).insert(new TaxNote("STANDARD_19", "Ignoriert."));
        verify(repository).deleteById("KLEINUNTERNEHMER");
        verify(repository).deleteById("REVERSE_CHARGE_EU");
        verify(repository).deleteById("NOT_TAXABLE_ABROAD");
        verify(repository, never()).deleteById("STANDARD_19");
    }

    @Test
    void refusesANoteLongerThan500Characters() {
        service.save(Map.of(TaxCategory.EXEMPT_4_21, "x".repeat(500)));
        Map<TaxCategory, String> tooLong = Map.of(TaxCategory.EXEMPT_4_21, "x".repeat(501));
        assertThatThrownBy(() -> service.save(tooLong)).isInstanceOf(SettingsException.class)
                .hasMessage("Der Hinweis „§ 4 Nr. 21“ hat höchstens 500 Zeichen.");
        verify(aggregates).insert(any());
    }
}
