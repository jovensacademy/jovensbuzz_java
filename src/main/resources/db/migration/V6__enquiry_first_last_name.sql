-- Flyway V6: Keep first and last name as entered in the contact form.
-- full_name stays the canonical display name (emails, admin, CSV). Historical
-- enquiries keep NULL here; the Sheets sync falls back to splitting full_name.

ALTER TABLE enquiries
    ADD COLUMN first_name VARCHAR(60) NULL AFTER full_name,
    ADD COLUMN last_name VARCHAR(60) NULL AFTER first_name;
