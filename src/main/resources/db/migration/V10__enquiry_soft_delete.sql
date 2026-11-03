-- Soft deletion of enquiries: the row, its history, notes, sync and email jobs are kept so records can be
-- restored and audit history stays intact. Deleted enquiries are hidden from lists, analytics and exports.
-- Additive only; Google Sheets rows are never touched.

ALTER TABLE enquiries
    ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL,
    ADD COLUMN deleted_by VARCHAR(64) NULL;

CREATE INDEX idx_enquiry_deleted_at ON enquiries (deleted_at);
