-- Use the public LinkedIn company page instead of the admin dashboard URL
UPDATE site_content SET content_value = 'https://www.linkedin.com/company/144002640/', updated_by = 'SYSTEM' WHERE content_key = 'social_linkedin';
