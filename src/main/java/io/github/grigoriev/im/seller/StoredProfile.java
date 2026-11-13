package io.github.grigoriev.im.seller;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The row of the one seller profile.
 *
 * @param id always {@link SellerService#ID}
 * @param profile the profile
 */
@Table("seller_profile")
record StoredProfile(@Id Integer id, @Embedded.Empty SellerProfile profile) {
}
