# 4. The full import goes only into an empty installation

- Status: accepted
- Date: 2026-11-14
- Recorded in: [PLAN.md](../PLAN.md), section 7 "Reports and export", item 4, and section 10 "Integration suite", item 7

## Context and problem

The full export moves all data and documents to a new host (principle "Own data"). Merging an export into an
installation that already has invoices would mix two number sequences, could duplicate numbers and would need to
change or delete issued invoices, which [ADR 0002](0002-write-once-by-triggers-and-an-app-role.md) forbids.

## Decision

`BackupService.restore` reads an export only into an installation without seller profile, customer, template or
invoice, and only from the same format and the same schema version. It runs in one transaction, locks its tables,
replaces users, settings and the audit log with those of the export, keeps the triggers on and checks every SHA-256
and seal before it commits. Afterward every session ends.

## Consequences

- No merge logic and no conflict rules: the result is the same data with the same SHA-256 values.
- A failed import leaves the installation empty, so it can be repeated.
- To restore over existing data, the operator sets up a new empty installation first. A `pg_dump` restore stays the
  way for the nightly backups (README, "Backup and restore").
- An export of another schema version needs an app of that version.
