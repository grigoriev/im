package io.github.grigoriev.im.numbering;

import org.springframework.data.repository.CrudRepository;

/** Reads the yearly counters. {@link NumberService} changes them, under a row lock. */
interface NumberSequenceRepository extends CrudRepository<NumberSequence, Integer> {
}
