package io.github.grigoriev.im.seller;

import org.springframework.data.repository.ListCrudRepository;

/** Reads and removes the changed tax notes. {@link TaxNoteService} writes them. */
interface TaxNoteRepository extends ListCrudRepository<TaxNote, String> {
}
