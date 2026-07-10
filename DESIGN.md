# Design: schema and queries

Every column here answers a question. If it does not answer a question, it does
not belong.

---

## Schema

```sql
CREATE TABLE jobs (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type              TEXT         NOT NULL,
    payload           JSONB        NOT NULL,
    status            TEXT         NOT NULL DEFAULT 'pending'
                                   CHECK (status IN ('pending','running','succeeded','dead')),

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    run_after         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    claimed_at        TIMESTAMPTZ,
    lease_expires_at  TIMESTAMPTZ,
    worker_id         TEXT,

    attempts          INT          NOT NULL DEFAULT 0,
    last_error        TEXT
);
```

### What each column is for

| Column | Question it answers | Written by |
|---|---|---|
| `id` | Which job? | API, on insert |
| `type` | Which handler runs this? | API, on insert |
| `payload` | What arguments does the handler get? | API, on insert |
| `status` | Where is this in its lifecycle? | API, worker, reaper |
| `created_at` | What is the claim order? | API, on insert |
| `run_after` | Is this job eligible yet? | API (defaults to `now()`); worker, on retry |
| `claimed_at` | When did execution begin? | worker, on claim |
| `lease_expires_at` | When should this be presumed abandoned? | worker, on claim and on every heartbeat |
| `worker_id` | Who currently owns this row? | worker, on claim |
| `attempts` | How many times has this been handed out? | worker (on throw), reaper (on reap) |
| `last_error` | Why did it die, three days later when I look? | worker, on failure |

### The columns worth defending

**`lease_expires_at`, not `claimed_at + constant`.** Both express a lease. The
difference is where the timeout *lives*.

With `WHERE claimed_at < now() - interval '30 seconds'`, the number 30 lives in
the reaper's code and applies to every job in the table. A `send-email` job
finishing in 200ms and a `generate-report` job taking four minutes get the same
deadline. Pick 30s and reports are murdered forever; pick 5m and a dead worker's
email sits stuck for five minutes.

With `lease_expires_at` stamped on the row at claim time, each job carries its
own deadline. The reaper's query never changes. Two things follow:

- **Per-type leases.** Email gets `now() + 30s`, report gets `now() + 5m`.
- **The lease is extendable.** A worker mid-job can push it forward. That is the
  heartbeat, and it is why the lease can be short even for long jobs.

The heartbeat decouples death-detection latency from job duration. Without it,
the lease must exceed the slowest job, and detection latency is pinned to that
value. This is the same reason Raft's election timeout is tuned to how fast you
want to notice a dead leader, not to how long the leader's work takes.

**`worker_id` is per thread, not per process.** Its only appearance is the guard
clause. If eight worker threads in one JVM share a process-level id, thread 3 can
successfully write an outcome for a job thread 7 claimed, and the guard passes
when it should not. The id's granularity must match the granularity of the thing
that claims a row.

Use `hostname:pid:thread-name` rather than a bare UUID. When the log says
*"worker lost lease on job X"*, you want to know which one.

**`run_after` makes backoff a column, not a sleep.** A job fails, backoff says
retry in 30 seconds, the worker writes `status = 'pending'`. With no `run_after`,
another worker claims it a millisecond later. You have no backoff — you have a
hot loop that burns five attempts before the downstream service has finished
restarting.

With `run_after = now() + backoff` and `AND run_after <= now()` in the claim
query, the job is simply *not eligible* until its time comes. Nobody blocks and
nobody schedules.

This also gives delayed jobs for free. Submit with `run_after = tomorrow 09:00`
and the queue is a scheduler.

**`status` as `TEXT` + `CHECK`, not a Postgres `ENUM`.** Adding a value to a
Postgres enum is a DDL migration; adding one to a check constraint is also a
migration, but a dropped-and-recreated constraint rather than a type alteration,
and it does not interact with the JDBC driver's type mapping. For four values
that will not change, the simplicity wins. Note the position and be able to
defend it either way.

---

## Indexes

```sql
CREATE INDEX jobs_claim_idx  ON jobs (status, run_after, created_at);
CREATE INDEX jobs_reaper_idx ON jobs (status, lease_expires_at);
```

An index is a sorted copy of some columns plus a pointer back to the row. A
multi-column index sorts by the first column, then breaks ties with the second,
and so on — a phone book ordered by surname, then first name. You can find every
*Nikolić* instantly. You cannot find every *Petar*, because they are scattered
one per surname.

That is the whole rule: an index on `(a, b, c)` helps only if the query
constrains `a` first. Prefix or nothing.

### The claim index

```sql
SELECT * FROM jobs
WHERE status = 'pending' AND run_after <= now()
ORDER BY created_at
LIMIT 1
FOR UPDATE SKIP LOCKED;
```

Every worker runs this, constantly, forever. It is the only query whose plan
matters.

- `status = 'pending'` is an **equality**. It collapses the index to one
  contiguous block. Most rows in a mature table are `succeeded`; they vanish.
- `run_after <= now()` is a **range**. Within that block, a range scan finds
  where eligibility stops.
- `created_at` is the **sort order**. Inside the block, rows are already sorted
  by it, so Postgres reads the first eligible row and stops. **There is no sort
  step.**

Equality, then range, then sort. That is the ordering, and reversing any two of
them costs you either the block or the free sort.

Without the index, Postgres sequentially scans every row on every poll of every
worker. With it, the claim touches a handful of index pages and one row, and does
not care that the table holds ten million rows.

This is why there is no cache in front of this table. The claim query's entire
purpose is to find a row **nobody else has**; a cache is a stale copy, and two
workers reading the same cached row both claim it. That reintroduces the exact
bug `SKIP LOCKED` exists to prevent, and it cannot then be fixed, because the
atomicity lived in the database and the cache routed around it.

### The reaper index

```sql
UPDATE jobs
SET status = 'pending', attempts = attempts + 1, worker_id = NULL
WHERE status = 'running' AND lease_expires_at < now();
```

Same shape: equality on `status`, range on `lease_expires_at`. No sort needed.

---

## The three queries

### Claim (worker, one transaction)

```sql
BEGIN;

SELECT id FROM jobs
WHERE status = 'pending' AND run_after <= now()
ORDER BY created_at
LIMIT 1
FOR UPDATE SKIP LOCKED;

UPDATE jobs
SET status = 'running',
    claimed_at = now(),
    lease_expires_at = now() + :lease,
    worker_id = :worker_id
WHERE id = :id;

COMMIT;
```

`FOR UPDATE` alone is correct but serializes the fleet: twenty workers queue
single-file on the row the first one locked, each waiting to receive a job that
is already taken. `SKIP LOCKED` makes a locked row *invisible* to the query, so
`LIMIT 1` returns the next one instead. Twenty workers, twenty distinct rows,
zero coordination.

The row lock is released at `COMMIT`. After that, nothing in Postgres protects
this job. `status = 'running'` is a **flag**, not a lock — a convention the code
honors. That asymmetry is why the reaper exists: a lock vanishes when its holder
dies, because the connection drops. A flag does not. A dead worker leaves a row
that *claims* someone is working on it, and only an `UPDATE` will ever change
that.

### Heartbeat (worker, mid-job, every `lease/3` or so)

```sql
UPDATE jobs
SET lease_expires_at = now() + :lease
WHERE id = :id AND worker_id = :worker_id AND status = 'running';
```

Nobody receives this. It is not a message. It is the worker pushing its own
deadline forward. A healthy worker never has an expired lease, so the reaper
never sees it.

Note the guard clause is here too. If the worker has already lost the lease, its
own heartbeat updates zero rows and it finds out immediately rather than at the
end of the job.

### Terminal write (worker)

```sql
UPDATE jobs
SET status = 'succeeded'
WHERE id = :id AND worker_id = :worker_id AND status = 'running';
```

Check the affected row count. Zero means the row moved on without you: a GC pause
starved the heartbeat thread, the lease lapsed, the reaper reclaimed it, and
another worker owns it now. Log it and abandon. The work you just did was wasted,
which is the price of at-least-once, and which you already agreed to pay.

**The check and the write are one statement.** A `SELECT` to verify ownership
followed by an `UPDATE` is a race — the reaper can steal the row between them.

---

## How the Java reaches the SQL

The three queries above are the whole system. Everything else is plumbing. So the
data-access layer is chosen by one test: **can it express these exact statements,
and can it tell me how many rows they touched?**

That question disqualifies the default.

### Why not JPA/Hibernate

Hibernate's model is dirty checking. You load an entity, mutate the object, and at
transaction commit Hibernate diffs it against the snapshot it took at load and
emits the `UPDATE` it inferred:

```java
job.setStatus("succeeded");   // no SQL, no WHERE clause, no return value
```

Two things break.

**It writes the `WHERE` clause, and it only knows the primary key.** The statement
that reaches Postgres is:

```sql
UPDATE jobs SET status = 'succeeded' WHERE id = ?
```

The guard clause is gone. There was no argument in `setStatus` to put it in.
Consider worker-3, GC-paused past its lease, reaped, its job handed to worker-7.
Worker-3 wakes and commits. `id` still matches, so the write lands: the row now
reads `status = 'succeeded'`, `worker_id = 'worker-7'`, while worker-7 is still
executing. The job ran twice — which the contract permits — but nothing in the
system *noticed*, which the contract does not.

**It returns `void`, and the SQL leaves later.** The row count is the entire
detection mechanism for row 6 of the failure model. A field assignment cannot hand
one back, and by the time the flush happens at commit the method has already
returned. There is nowhere to catch it.

Neither is a configuration problem. `@Modifying` + `@Query` gets both back, but at
that point the SQL is a string and the entity mapping is doing no work. The ORM has
been paid for and switched off.

### Why not jOOQ

jOOQ passes the test. It is a typed SQL DSL — `.forUpdate().skipLocked()` compiles
to the right statement, `.execute()` returns the row count, and a misspelled column
is a compile error rather than a 3 a.m. one. On a codebase with a hundred queries
that guarantee is worth the build step.

This codebase has six, and they will not change. The typo risk jOOQ buys down is
already caught by step 3's test on the first run. What it costs is a code-generation
phase between `git clone` and a running app, and one layer of translation between a
reader and the line this project exists to explain.

### JdbcTemplate

```java
int rows = jdbc.update("""
        UPDATE jobs SET status = 'succeeded'
        WHERE id = ? AND worker_id = ? AND status = 'running'
        """, id, workerId);

if (rows == 0) {
    log.warn("lost lease on job {}", id);   // the reaper got here first
    return;
}
```

The guard clause is visible. The row count is a local variable. The statement in
the source and the statement in Postgres are the same characters.

The cost is real and worth naming: the SQL is a `String`, so the compiler cannot see
it. A misspelled column name is a runtime failure. That is acceptable here for the
same reason jOOQ is unnecessary here — six queries, each covered by a test that
fails loudly.

**The SQL is the artifact.** Hibernate would hide it, jOOQ would abstract it, and
this document would be describing code that does not exist.

### Schema: Flyway, not `schema.sql`

The DDL at the top of this file is checked in verbatim as `V1__create_jobs.sql`.

`schema.sql` would run that same DDL, but it re-runs on every startup against
whatever is already there, which makes the second migration — adding `partition_key`
to a table with ten million rows in it — an unanswerable question. Flyway records
each applied script, and its checksum, in a `flyway_schema_history` table: new
scripts run, old ones do not, and a migration edited after the fact is caught rather
than silently diverging.

Spring's own default settles it. `spring.sql.init.mode` defaults to `embedded`:
`schema.sql` runs against an in-memory H2 and does nothing at all against a real
Postgres unless you flip it. Spring considers `schema.sql` a test-database
convenience. This queue's first line of contract is that durability is Postgres's.

Not `ddl-auto` either, for the reason above: it would generate the schema from
annotated Java classes. The `CHECK` constraint, the `jsonb` column, and the
column order of `jobs_claim_idx` — every line of this document that is worth
defending — would become a derived artifact of code that has no idea why any of
it is there.

---

## Reapers, plural

The reaper is a `@Scheduled` bean inside the application. Deploy the JAR to three
machines for API availability and you have three reapers, because each JVM boots
Spring and Spring wires the bean. You did not choose this; it came with the
deployment.

It does not need fixing.

Two reapers fire the same `UPDATE` on the same expired row. Postgres takes a row
lock. The first commits. The second blocks, then re-evaluates its `WHERE` clause
against the *committed* value — which now reads `status = 'pending'`. The
predicate is false. Zero rows. `attempts` incremented once.

Postgres serialized them. No election, no lock table, no coordination. The safety
comes from the predicate being inside the write.

Note what would *not* be safe: reading `attempts`, computing `attempts + 1` in
Java, and writing it back. Both reapers read 3, both write 4. A lost update. The
statement must be `SET attempts = attempts + 1`.

### If a reaper dies

Nothing. A reaper holds no state — no lease, no memory of the last tick. It runs
one stateless `UPDATE` and forgets. A dead reaper leaves nothing behind.

Contrast a dead worker, which leaves a `running` row that will never change
unless something intervenes. That asymmetry is the reaper's entire reason for
existing.

If *every* reaper dies — all instances down — expired jobs sit in `running`.
Nothing is lost; the rows are durable. When an instance returns, its first tick
sweeps every row that expired during the outage in one `UPDATE`, because the
predicate is evaluated against the current table, not against events that were
missed. There is no backlog to replay. **A predicate-based system that goes down
misses nothing; it simply was not running the query.**

The blast radius of a dead reaper is *recovery latency*. Not chaos. Degradation,
bounded and named.

---

## Push state into the database

Every process in this system is disposable. Workers hold no state — kill one and
its lease expires. Reapers hold no state — kill one and another ticks. The API
holds no state — kill it and rows stay on disk.

Postgres is the only thing that has to survive, and that is Postgres's job.
