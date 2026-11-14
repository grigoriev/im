-- A second tax number for teaching under §4 Nr. 21 UStG, when the Finanzamt gave one (docs/PLAN.md, section 6).
ALTER TABLE seller_profile ADD COLUMN tax_number_teaching VARCHAR(32);
