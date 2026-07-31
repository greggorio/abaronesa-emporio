-- Add user_id and last_seen_at columns to notification_subscriptions table
-- This allows linking push notification subscriptions to authenticated users

ALTER TABLE notification_subscriptions
    ADD COLUMN user_id BIGINT NULL,
    ADD COLUMN last_seen_at TIMESTAMP NULL;

-- Create index on user_id for performance
CREATE INDEX idx_notification_subscriptions_user_id ON notification_subscriptions (user_id);