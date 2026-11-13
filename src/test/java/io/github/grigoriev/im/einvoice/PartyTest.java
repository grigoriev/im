package io.github.grigoriev.im.einvoice;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class PartyTest {

    @Test
    void requiresTheAddress() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Party(null, "s", "p", "c", "DE", null, null, null, null, null))
                .withMessage("name");
        assertThatNullPointerException()
                .isThrownBy(() -> new Party("n", null, "p", "c", "DE", null, null, null, null, null))
                .withMessage("street");
        assertThatNullPointerException()
                .isThrownBy(() -> new Party("n", "s", null, "c", "DE", null, null, null, null, null))
                .withMessage("postcode");
        assertThatNullPointerException()
                .isThrownBy(() -> new Party("n", "s", "p", null, "DE", null, null, null, null, null))
                .withMessage("city");
        assertThatNullPointerException()
                .isThrownBy(() -> new Party("n", "s", "p", "c", null, null, null, null, null, null))
                .withMessage("countryCode");
    }
}
