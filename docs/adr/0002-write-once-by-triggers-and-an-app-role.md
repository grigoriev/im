# 2. Write once by database triggers and a separate app role

- Status: accepted
- Date: 2026-11-15 (triggers on issued invoices since 2026-11-14, the separate role since 2026-11-15, the audit log
  written once since 2026-11-17)
- Recorded in: [PLAN.md](../PLAN.md), section 3 "Principles", item 2, and section 6 "Invoice status", item 2

## Context and problem

Issued invoices, their documents, dispatches, payments, reminders, business documents, expense receipts, original
files, reasons of number gaps and the audit log must not change afterward (GoBD, Unveränderbarkeit). A check in the
Java code alone does not protect against a bug, a later change of the code or a manual statement in the database.
A trigger alone does not help either if the app connects as the owner of the tables, who may switch it off.

## Decision

- Database triggers refuse every forbidden `UPDATE` and `DELETE`, for example `invoice_frozen`,
  `invoice_document_frozen`, `payment_frozen`, `expense_frozen`, `invoice_original_frozen`, `number_gap_kept` and
  `audit_event_kept`. Only the status of an invoice moves on.
- The app connects as its own role (`IM_DB_USER`, for example `im_app`) that may only read and write rows. Flyway
  migrates as the owner (`IM_DB_OWNER`) in a step of its own (`java -jar im.jar migrate`), and only that step gets the
  owner's password.
- `ConnectionGuard` refuses to start while the app connects as the owner or as a superuser. `SchemaCheck` refuses to
  start on a schema older than the app.

## Consequences

- Whoever gets into the app, or finds a bug in it, cannot change an issued invoice or delete a log entry.
- Operations need two roles, and the role of the app must exist before the first migration and before a restore.
- The full import works with the triggers on: an issued invoice goes in as a draft, gets its lines and documents,
  and only then its status (see [ADR 0004](0004-import-only-into-an-empty-installation.md)).
- Whoever holds the owner's password can still change data. The seals make such a change visible
  ([ADR 0003](0003-hmac-seals-with-an-integrity-key.md)).
