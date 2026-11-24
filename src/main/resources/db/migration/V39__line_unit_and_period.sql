-- The unit of a line with quantity and the day or period of its service (docs/PLAN.md, section 7). Lines from before
-- count pieces and have no period, as their snapshots do. Adding columns fires no row trigger, so issued lines stay.
ALTER TABLE invoice_line ADD COLUMN unit VARCHAR(16) NOT NULL DEFAULT 'PIECE',
    ADD COLUMN service_from DATE, ADD COLUMN service_to DATE,
    ADD CONSTRAINT invoice_line_period CHECK ((service_from IS NULL) = (service_to IS NULL)
        AND service_to >= service_from);
ALTER TABLE invoice_template_line ADD COLUMN unit VARCHAR(16) NOT NULL DEFAULT 'PIECE',
    ADD COLUMN service_from DATE, ADD COLUMN service_to DATE,
    ADD CONSTRAINT invoice_template_line_period CHECK ((service_from IS NULL) = (service_to IS NULL)
        AND service_to >= service_from);
