package io.github.grigoriev.im.invoice;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/** Stores the invoices with their lines. */
interface InvoiceRepository extends ListCrudRepository<Invoice, Long> {

    /**
     * Lists all invoices, the newest first.
     *
     * @return the invoices
     */
    List<Invoice> findAllByOrderByIdDesc();

    /**
     * Remembers the template a draft came from, for the text of its e-mail.
     *
     * @param id the draft
     * @param templateId the template
     */
    @Modifying
    @Query("UPDATE invoice SET template_id = :templateId WHERE id = :id")
    void linkTemplate(long id, long templateId);

    /**
     * Returns the template an invoice came from.
     *
     * @param id the invoice
     * @return the template ID, {@code null} without a template or after its deletion
     */
    @Query("SELECT template_id FROM invoice WHERE id = :id")
    Long templateId(long id);

    /**
     * Links a Stornorechnung draft to the invoice it cancels.
     *
     * @param id the draft
     * @param cancelsId the cancelled invoice
     */
    @Modifying
    @Query("UPDATE invoice SET cancels_id = :cancelsId WHERE id = :id")
    void linkCancels(long id, long cancelsId);

    /**
     * Returns the invoice a Stornorechnung cancels.
     *
     * @param id the Stornorechnung
     * @return the cancelled invoice's ID, empty for an invoice
     */
    @Query("SELECT cancels_id FROM invoice WHERE id = :id AND cancels_id IS NOT NULL")
    Optional<Long> cancelsId(long id);

    /**
     * Returns the Stornorechnung of an invoice, a draft included.
     *
     * @param id the invoice
     * @return the Stornorechnung's ID, empty if there is none
     */
    @Query("SELECT id FROM invoice WHERE cancels_id = :id")
    Optional<Long> cancellationId(long id);
}
