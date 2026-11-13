package io.github.grigoriev.im.seller;

import org.springframework.data.repository.CrudRepository;

/** Reads the seller profile. {@link SellerService} writes it. */
interface StoredProfileRepository extends CrudRepository<StoredProfile, Integer> {
}
