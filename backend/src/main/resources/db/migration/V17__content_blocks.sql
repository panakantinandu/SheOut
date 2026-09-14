-- Editable app copy, served by the content module and edited from the ops
-- console. Replaces strings that were hardcoded in the two apps.
--
-- The seed values below are the apps' exact strings at the moment of the
-- move, generated from the source rather than retyped, so the first release
-- reads identically to the one before it. The apps keep those same strings as
-- a fallback for a first load with no network; after that, this table wins.
--
-- Plain text only. Line breaks are kept; nothing is interpreted as HTML or
-- markdown, so no edit here can put markup or script into either app.

CREATE TABLE content_blocks (
    id           UUID          PRIMARY KEY,
    -- Dotted and area-first ("home.banner.title", "faq.driver.3.answer"), so
    -- a whole section is one prefix read and the console groups by the first
    -- segment. Fixed by this migration: editors change values, not keys.
    content_key  VARCHAR(100)  NOT NULL UNIQUE,
    value        VARCHAR(2000) NOT NULL,
    -- Where the text appears, in words, for whoever is editing it.
    description  VARCHAR(200)  NOT NULL,
    -- Optimistic locking: two operators saving the same block cannot
    -- silently overwrite each other - the second is told it changed.
    version      BIGINT        NOT NULL,
    -- Same audit convention as the rest of the project: who changed it last,
    -- beside updated_at. NULL means still the seeded value.
    updated_by   UUID,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL
);

INSERT INTO content_blocks (id, content_key, value, description, version, updated_by, created_at, updated_at) VALUES
    (gen_random_uuid(), 'home.banner.title', 'Ride with confidence', 'Rider app, Home: the purple banner''s headline', 0, NULL, now(), now()),
    (gen_random_uuid(), 'home.banner.subtitle', 'Safe rides, verified women partners', 'Rider app, Home: the line under the banner headline', 0, NULL, now(), now()),
    (gen_random_uuid(), 'home.community.title', 'Women Supporting Women', 'Rider app, Home: the community card''s title', 0, NULL, now(), now()),
    (gen_random_uuid(), 'home.community.subtitle', 'Safe · Empowered · Together', 'Rider app, Home: the community card''s tagline', 0, NULL, now(), now()),
    (gen_random_uuid(), 'help.customer.intro.title', 'We are here to help', 'Rider app, Help & Support: the card at the top, first line', 0, NULL, now(), now()),
    (gen_random_uuid(), 'help.customer.intro.subtitle', 'Tell us what happened and we will reply here.', 'Rider app, Help & Support: the card at the top, second line', 0, NULL, now(), now()),
    (gen_random_uuid(), 'help.driver.intro.title', 'Driver support', 'Partner app, Help & Support: the card at the top, first line', 0, NULL, now(), now()),
    (gen_random_uuid(), 'help.driver.intro.subtitle', 'Tell us what happened and we will reply here.', 'Partner app, Help & Support: the card at the top, second line', 0, NULL, now(), now()),
    (gen_random_uuid(), 'about.body', 'Rides and deliveries built around women''s safety - women riders, women drivers.', 'Rider app, About SheOut: the description under the name', 0, NULL, now(), now()),
    (gen_random_uuid(), 'about.feature.1.title', 'Verified drivers', 'Rider app, About SheOut: first feature, heading', 0, NULL, now(), now()),
    (gen_random_uuid(), 'about.feature.1.body', 'Every driver passes an ID and police-verification review before they can accept a trip.', 'Rider app, About SheOut: first feature, text', 0, NULL, now(), now()),
    (gen_random_uuid(), 'about.feature.2.title', 'Emergency contacts', 'Rider app, About SheOut: second feature, heading', 0, NULL, now(), now()),
    (gen_random_uuid(), 'about.feature.2.body', 'Add contacts to your account and one tap on SOS sends them your location by SMS.', 'Rider app, About SheOut: second feature, text', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.customer.1.question', 'How do I cancel a booking?', 'Rider app, Help & Support: common question 1', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.customer.1.answer', 'Open the trip from Bookings and use Cancel, any time before the trip starts. You will be asked why, in one tap. Cancelling often enough is looked at by a person here, never acted on automatically.', 'Rider app, Help & Support: answer to question 1', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.customer.2.question', 'How do I contact my partner?', 'Rider app, Help & Support: common question 2', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.customer.2.answer', 'Through chat on the trip screen, from the moment she accepts until the trip ends. Phone numbers are never shared in either direction, and cannot be sent through chat. If something needs a call, call us and we will handle it.', 'Rider app, Help & Support: answer to question 2', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.customer.3.question', 'When do I see who is picking me up?', 'Rider app, Help & Support: common question 3', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.customer.3.answer', 'Once she accepts your trip. You will see her name, photo, vehicle and registration number, and her rating. Nothing about her is shown before that, because until she accepts she may not be the one coming.', 'Rider app, Help & Support: answer to question 3', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.customer.4.question', 'Who can see my SOS alert?', 'Rider app, Help & Support: common question 4', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.customer.4.answer', 'The emergency contacts saved on your account are sent an SMS with your live location, and the alert is raised to SheOut operators.', 'Rider app, Help & Support: answer to question 4', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.customer.5.question', 'How am I charged?', 'Rider app, Help & Support: common question 5', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.customer.5.answer', 'Per trip, by UPI or cash. No card is stored on your account - see Payment History for what you have paid.', 'Rider app, Help & Support: answer to question 5', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.1.question', 'Why can I not go online?', 'Partner app, Help & Support: common question 1', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.1.answer', 'Three things have to be in place: your ID and police verification both approved, and a profile photo on your account. Check Verification to see which checks are still pending, and My Profile to add a photo.', 'Partner app, Help & Support: answer to question 1', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.2.question', 'Why do I need a profile photo?', 'Partner app, Help & Support: common question 2', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.2.answer', 'Riders see it when you are on your way. It is how a woman getting into a stranger''s vehicle at night checks she has the right one. It is never shown to anyone before you accept a trip.', 'Partner app, Help & Support: answer to question 2', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.3.question', 'Why am I not getting requests?', 'Partner app, Help & Support: common question 3', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.3.answer', 'You must be online, verified, and allowing location access - requests are offered to the nearest available partners first, then further out.', 'Partner app, Help & Support: answer to question 3', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.4.question', 'How do I contact my rider?', 'Partner app, Help & Support: common question 4', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.4.answer', 'Through chat on the trip screen, from the moment you accept until the trip ends. Phone numbers are never shared in either direction and cannot be sent through chat. If something needs a call, call us.', 'Partner app, Help & Support: answer to question 4', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.5.question', 'What happens if I cancel a trip?', 'Partner app, Help & Support: common question 5', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.5.answer', 'You will be asked why, in one tap. Cancelling often relative to the trips you take on is reviewed by a person here - never acted on automatically - so the reason you give is your side of it.', 'Partner app, Help & Support: answer to question 5', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.6.question', 'How are my earnings worked out?', 'Partner app, Help & Support: common question 6', 0, NULL, now(), now()),
    (gen_random_uuid(), 'faq.driver.6.answer', 'Earnings are the total of your completed trips. There is no separate payout module yet, so Earnings shows trip totals rather than settled payouts.', 'Partner app, Help & Support: answer to question 6', 0, NULL, now(), now());
