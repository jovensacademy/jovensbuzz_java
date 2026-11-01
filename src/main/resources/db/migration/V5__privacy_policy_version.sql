-- Flyway V5: Record applicable privacy policy version at enquiry submission
-- Historical enquiries retain privacy_consent=true with privacy_policy_version=NULL.
-- New submissions record the privacy policy version in effect.

ALTER TABLE enquiries
    ADD COLUMN privacy_policy_version VARCHAR(32) NULL AFTER privacy_consent;
