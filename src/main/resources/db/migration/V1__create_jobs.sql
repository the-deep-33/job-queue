CREATE TABLE jobs(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type TEXT NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending', 'succeeded', 'dead', 'running')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    run_after TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    attempts INT DEFAULT 0 NOT NULL,
    max_attempts INT DEFAULT 3 NOT NULL,
    worker_id TEXT,
    last_error TEXT
);

CREATE INDEX jobs_claim_idx ON jobs (status, run_after, created_at);

CREATE INDEX jobs_reaper_idx ON jobs (status, lease_expires_at);

