-- Flyway V2: Seed Initial Services, FAQs, Site Content and Admin User
-- Password for jovens_buzz_2026 is Jovensbuzz@2026 (BCrypt encoded)
-- BCrypt: $2a$10$i2xM8R53iK0KvhM2s9U4i.Qc5WomW8aVom9a18N7J9n419m1lH7rO

INSERT INTO admin_users (username, password_hash, display_name, email, role, enabled, created_at, updated_at)
VALUES (
    'jovens_buzz_2026',
    '$2a$10$wE9aW691i/89J13f1k2KgeN8sE7bV8g6/gq7FvjQ9J9U6e3O4iMgy',
    'Jovens Buzz Admin',
    'admin@jovensbuzz.com',
    'ROLE_ADMIN',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- Seed Services
INSERT INTO services (title, slug, description, icon, display_order, published) VALUES
('Social Media Strategy', 'social-media-strategy', 'A clear plan for what to post, why and when to maximize reach and drive business revenue.', 'strategy', 1, TRUE),
('Content Creation', 'content-creation', 'Captions, stories, carousels and high-converting graphics that build brand trust and engagement.', 'content', 2, TRUE),
('Creative Design', 'creative-design', 'Scroll-stopping graphics, custom illustrations and visual assets that make your brand memorable.', 'design', 3, TRUE),
('Short-Form Video Production', 'short-form-video', 'Viral Reels, TikToks and YouTube Shorts scripted and edited to capture attention in seconds.', 'video', 4, TRUE),
('Social Media Management', 'social-media-management', 'Full-service publishing, community moderation and monthly performance reporting so you run your business.', 'management', 5, TRUE),
('Brand Identity', 'brand-identity', 'Logo systems, color palettes, typography and guidelines for a unified presence across all channels.', 'branding', 6, TRUE),
('Website Design and Development', 'website-design-development', 'Modern, high-performance, responsive websites engineered to convert visitors into loyal clients.', 'web', 7, TRUE),
('Digital Marketing Campaigns', 'digital-marketing-campaigns', 'Targeted pay-per-click, meta ad campaigns, and multi-channel marketing to accelerate lead acquisition.', 'campaigns', 8, TRUE)
ON DUPLICATE KEY UPDATE title=VALUES(title), description=VALUES(description);

-- Seed FAQs
INSERT INTO faqs (question, answer, display_order, published) VALUES
('My business gets likes, but no enquiries. What needs to change?', 'Likes alone don’t guarantee business results. We assess your content, audience, profile, messaging and calls to action to identify where potential customers may be dropping off. We then build content around customer needs, trust and clear next steps to encourage meaningful enquiries.', 1, TRUE),
('Can you create content for my business if I don’t have professional photos or videos?', 'Yes. We can create branded graphics, educational carousels, promotional creatives and text-led content using the assets available. Where original photography or on-location video is important, we’ll discuss the resources needed and suitable options before production.', 2, TRUE),
('How do you market a business in a crowded local market?', 'We look at your competitors, target customers and what makes your business different. From there, we develop content that communicates your strengths, answers customer questions and gives people a reason to choose you instead of another local option.', 3, TRUE),
('What if I don’t know what to post every week?', 'You don’t need to come up with ideas yourself. We plan a content calendar around your business goals, audience interests, services, seasonal opportunities and promotional priorities. Your content can include educational posts, brand stories, offers, short-form videos and customer-focused topics.', 4, TRUE),
('Can social media help my business get discovered by nearby customers?', 'Yes. We can create locally relevant content, optimize your social profiles and support your local visibility strategy. Depending on your needs, Google Business Profile optimization and location-targeted paid campaigns can also help potential customers discover your business.', 5, TRUE),
('Do I need to run paid ads, or can organic content grow my business?', 'You can build awareness and engagement through organic content without running ads. Paid campaigns may help you reach specific audiences faster or support lead-generation goals. The right approach depends on your objectives, competition and budget; advertising spend is separate from management fees unless explicitly included in your package.', 6, TRUE),
('How do you measure whether your content is generating business value?', 'We evaluate performance against your goals, not just likes. Depending on the campaign, we review reach, engagement, profile visits, website clicks, enquiries and other available conversion data. These insights help us identify which content deserves more attention and what needs improvement.', 7, TRUE),
('How much time will I need to spend working with your team each month?', 'We handle the agreed planning, content creation and publishing tasks so you can focus on running your business. You’ll need to provide initial business information, share relevant updates or assets, and review content for approval. The time required depends on your package and how quickly feedback is provided.', 8, TRUE)
ON DUPLICATE KEY UPDATE question=VALUES(question), answer=VALUES(answer);

-- Seed Site Content
INSERT INTO site_content (content_key, content_value, content_type, updated_by) VALUES
('hero_headline', 'Making Small Businesses Look Big', 'TEXT', 'SYSTEM'),
('hero_eyebrow', 'Creative. Strategic. Results-Driven.', 'TEXT', 'SYSTEM'),
('hero_description', 'Strategy, content, design and digital experiences that help ambitious businesses build visibility and grow.', 'TEXT', 'SYSTEM'),
('about_title', 'The Creative Power Behind Your Growth.', 'TEXT', 'SYSTEM'),
('about_description', 'We build goal-driven strategies that attract the right audience, strengthen your brand, and drive business growth.', 'TEXT', 'SYSTEM'),
('contact_office', 'Dallas, TX, USA', 'TEXT', 'SYSTEM'),
('contact_phone', '+1 (959) 200-0495', 'TEXT', 'SYSTEM'),
('contact_email', 'jovensbuzz@gmail.com', 'TEXT', 'SYSTEM'),
('social_instagram', 'https://instagram.com/jovensbuzz', 'URL', 'SYSTEM'),
('social_facebook', 'https://facebook.com/jovensbuzz', 'URL', 'SYSTEM'),
('social_linkedin', 'https://linkedin.com/company/jovensbuzz', 'URL', 'SYSTEM'),
('social_youtube', 'https://youtube.com/@jovensbuzz', 'URL', 'SYSTEM')
ON DUPLICATE KEY UPDATE content_value=VALUES(content_value);
