# 3. HMAC seals with a key outside the database

- Status: accepted
- Date: 2026-11-15
- Recorded in: [PLAN.md](../PLAN.md), section 3 "Principles", item 2, and section 6 "Invoice status", item 2

## Context and problem

A SHA-256 next to a file shows accidental damage, but whoever can change the file in the database can change its
SHA-256 too. The owner of the tables can also switch off the triggers of
[ADR 0002](0002-write-once-by-triggers-and-an-app-role.md). A change of that kind must at least become visible.

## Decision

Each stored PDF and XML, the snapshot of an invoice, each business document, each expense receipt and each original
file of an external invoice carries a seal: an HMAC-SHA256 with the key `IM_INTEGRITY_KEY` over what the file belongs
to and its SHA-256 (`Seal`). The key lives in the environment of the app, never in the database or in a backup.
Every read checks SHA-256 and seal (`DocumentStore`, `InvoiceIntegrity`, `DocumentService`, `OriginalStore`).
`IntegrityMonitor` checks everything at startup, every night and on request. A changed file stays out of download,
e-mail and archive; the invoice page, the overview and the audit log name the problem.

## Consequences

- A change made with the owner's password, but without the key, shows.
- The key must be at least 32 random characters and kept apart from the backups. A restore or an import needs the
  same key; with another one every seal fails.
- A lost key cannot be recovered: the documents stay in the database, but the app keeps them back.
- Migration V29 (`SealMigration`) sealed the documents that existed before.
