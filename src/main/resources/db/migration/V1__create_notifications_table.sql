CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_notifications_created_at
ON notifications(created_at);