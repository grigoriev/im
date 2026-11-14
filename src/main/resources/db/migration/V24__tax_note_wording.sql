-- The default tax notes changed after the legal review (docs/PLAN.md, "Legal review"). Templates and drafts hold a copy
-- of the note; the old default wording gives way to the new one there. Issued invoices keep their text.
UPDATE invoice_template
   SET tax_note = replace(replace(tax_note,
           'Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.',
           'Für diese Leistung gilt die Steuerbefreiung für Kleinunternehmer (§ 19 Abs. 1 UStG).'),
           'Steuerfrei gemäß § 4 Nr. 21 UStG.', 'Steuerfreie Unterrichtsleistung gemäß § 4 Nr. 21 UStG.')
 WHERE tax_note LIKE '%Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.%'
    OR tax_note LIKE '%Steuerfrei gemäß § 4 Nr. 21 UStG.%';

UPDATE invoice
   SET tax_note = replace(replace(tax_note,
           'Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.',
           'Für diese Leistung gilt die Steuerbefreiung für Kleinunternehmer (§ 19 Abs. 1 UStG).'),
           'Steuerfrei gemäß § 4 Nr. 21 UStG.', 'Steuerfreie Unterrichtsleistung gemäß § 4 Nr. 21 UStG.')
 WHERE status = 'DRAFT'
   AND (tax_note LIKE '%Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.%'
        OR tax_note LIKE '%Steuerfrei gemäß § 4 Nr. 21 UStG.%');
