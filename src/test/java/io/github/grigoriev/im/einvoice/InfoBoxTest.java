package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class InfoBoxTest {

    @Test
    void isAUtilityClassWithAPrivateConstructor() throws Exception {
        Constructor<InfoBox> constructor = InfoBox.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        constructor.setAccessible(true);
        assertThat(constructor.newInstance()).isNotNull();
    }

    @Test
    void breaksThePartnerLabelOnlyForAValueThatDoesNotFitBesideIt() {
        assertThat(InfoBox.breaksPartnerLabel(null)).isFalse();
        assertThat(InfoBox.breaksPartnerLabel("K-17")).isFalse();
        assertThat(InfoBox.breaksPartnerLabel("x".repeat(InfoBox.BESIDE_PARTNER_LABEL))).isFalse();
        assertThat(InfoBox.breaksPartnerLabel("x".repeat(InfoBox.BESIDE_PARTNER_LABEL + 1))).isTrue();
        assertThat(InfoBox.BESIDE_PARTNER_LABEL).as("measured in InvoicePdfRendererTest").isEqualTo(15);
    }
}
