package io.github.grigoriev.im.seller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProfilePartTest {

    @Test
    void findsAPartByItsSlug() {
        assertThat(ProfilePart.of("anschrift")).contains(ProfilePart.ADDRESS);
        assertThat(ProfilePart.of("kontakt")).contains(ProfilePart.CONTACT);
        assertThat(ProfilePart.of("steuer")).contains(ProfilePart.TAX);
        assertThat(ProfilePart.of("bank")).contains(ProfilePart.BANK);
        assertThat(ProfilePart.of("andere")).isEmpty();
        assertThat(ProfilePart.of(null)).isEmpty();
        assertThat(ProfilePart.BANK.label()).isEqualTo("Bankverbindung");
    }

    @Test
    void thePartsCoverEveryFieldOnce() {
        List<String> fields = Arrays.stream(ProfilePart.values()).flatMap(part -> part.fields().stream()).toList();
        assertThat(fields).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(
                Arrays.stream(SellerProfile.class.getRecordComponents()).map(RecordComponent::getName).toList());
    }

    /** Each part takes exactly its own fields from the input: every field of the record is checked by name. */
    @ParameterizedTest
    @EnumSource(ProfilePart.class)
    void takesItsFieldsFromTheInputAndTheRestFromTheStoredProfile(ProfilePart part) throws Exception {
        SellerProfile stored = SellerTestData.profile();
        SellerProfile input = new SellerProfile("n", "s", "p", "c", "AT", "k", "e", "t", "x", "v", "i", "b",
                TaxStatus.REGULAR, "z", "w", "m", "h");
        SellerProfile merged = part.merge(stored, input);
        for (RecordComponent component : SellerProfile.class.getRecordComponents()) {
            Object expected = component.getAccessor().invoke(part.fields().contains(component.getName()) ? input : stored);
            assertThat(component.getAccessor().invoke(merged)).as(component.getName()).isEqualTo(expected);
        }
    }

    @Test
    void theFormShowsTheNameWhereContactAndHolderAreEmpty() {
        SellerProfile shown = SellerTestData.profile().withNameAsDefault();
        assertThat(shown.contactName()).isEqualTo("Erika Mustermann");
        assertThat(shown.accountHolder()).isEqualTo("Erika Mustermann");
        SellerProfile own = new SellerProfile("Erika", null, null, null, null, "Max", null, null, null, null, null,
                null, null, null, null, null, " ");
        assertThat(own.withNameAsDefault().contactName()).isEqualTo("Max");
        assertThat(own.withNameAsDefault().accountHolder()).isEqualTo("Erika");
        SellerProfile none = new SellerProfile(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        assertThat(none.withNameAsDefault()).isEqualTo(none);
        SellerProfile holder = new SellerProfile("Erika", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "Konto GmbH");
        assertThat(holder.withNameAsDefault().accountHolder()).isEqualTo("Konto GmbH");
    }
}
