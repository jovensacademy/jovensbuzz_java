-- Update the public contact email and social profile links
UPDATE site_content SET content_value = 'hello@jovensbuzz.com', updated_by = 'SYSTEM' WHERE content_key = 'contact_email';
UPDATE site_content SET content_value = 'https://www.linkedin.com/company/144002640/admin/dashboard/', updated_by = 'SYSTEM' WHERE content_key = 'social_linkedin';
UPDATE site_content SET content_value = 'https://www.facebook.com/profile.php?id=61595132745256', updated_by = 'SYSTEM' WHERE content_key = 'social_facebook';
UPDATE site_content SET content_value = 'https://www.instagram.com/jovensbuzz/', updated_by = 'SYSTEM' WHERE content_key = 'social_instagram';
