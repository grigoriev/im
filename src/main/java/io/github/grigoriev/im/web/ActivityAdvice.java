package io.github.grigoriev.im.web;

import io.github.grigoriev.im.seller.Activity;
import io.github.grigoriev.im.seller.SellerProfile;
import io.github.grigoriev.im.seller.SellerService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Tells every page whether teaching has a tax number of its own. Only then do customers and drafts offer the business
 * ("Tätigkeit") that picks the tax number of an invoice.
 */
@ControllerAdvice
public class ActivityAdvice {

    private final SellerService sellers;

    /**
     * Creates the advice.
     *
     * @param sellers the seller profile
     */
    public ActivityAdvice(SellerService sellers) {
        this.sellers = sellers;
    }

    /**
     * Tells whether the seller has a second tax number for teaching.
     *
     * @return {@code true} with two tax numbers
     */
    @ModelAttribute("twoTaxNumbers")
    public boolean twoTaxNumbers() {
        return sellers.find().map(SellerProfile::twoTaxNumbers).orElse(false);
    }

    /**
     * Returns the businesses to choose from.
     *
     * @return the businesses
     */
    @ModelAttribute("activities")
    public Activity[] activities() {
        return Activity.values();
    }
}
