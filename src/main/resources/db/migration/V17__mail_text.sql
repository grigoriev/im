-- The default e-mail text is a setting and needs more room. The invoice remembers its template, whose e-mail text it
-- sends (docs/PLAN.md, section 7). The e-mail text is no part of the invoice: it may change after the issue.
ALTER TABLE app_setting ALTER COLUMN value TYPE VARCHAR(4000);

ALTER TABLE invoice ADD COLUMN template_id BIGINT REFERENCES invoice_template (id) ON DELETE SET NULL;
