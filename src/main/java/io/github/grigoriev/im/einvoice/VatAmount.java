package io.github.grigoriev.im.einvoice;

import java.math.BigDecimal;

/**
 * VAT of one rate on an invoice (BG-23).
 *
 * @param rate VAT rate in percent
 * @param base sum of the net line amounts at this rate
 * @param vat VAT on the base, rounded half up to cents
 */
public record VatAmount(BigDecimal rate, BigDecimal base, BigDecimal vat) {
}
