CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date TIMESTAMPTZ,
    status VARCHAR(16),
    completion_attempts INT NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMPTZ
);

CREATE INDEX idx_event_publication_serialized_event_hash
    ON event_publication USING hash (serialized_event);

CREATE INDEX idx_event_publication_by_completion_date
    ON event_publication (completion_date);
