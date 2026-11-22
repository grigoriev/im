# 5. External invoices and numbers by hand

- Status: accepted
- Date: 2026-11-22
- Recorded in: [PLAN.md](../PLAN.md), section 7 "External invoices"

## Context and problem

Some customers demand their own invoice form, and the invoices of 2026 were made before the app. Reports, the §19
threshold, payments and reminders must still see them, and the GoBD rules for keeping and numbering still apply.

## Decision

- An invoice made outside the app is registered as an external invoice (format `EXTERNAL`). The app generates no
  PDF and no XML. It keeps the original files (PDF, XLSX, DOCX or XML, at most 25 MB each), written once and sealed
  (`OriginalStore`, `InvoiceOriginal`). The facts are typed in by hand and form the snapshot.
- In a year of the app the registration takes the next number under the row lock of the issue
  ([ADR 0001](0001-issue-in-one-transaction.md)) and refuses a file with another number. No number is reserved.
- A year before the app is switched to numbers by hand (`ManualNumbers`). Its invoices come only as external
  invoices with the number of their file, in any order, of the form year and counter, each counter once. Each gap
  needs a reason, written once and logged. "Jahr abschließen" works only without an unexplained gap and continues the
  counter after the highest number.

## Consequences

- Dispatch, payments, reminders, the Stornorechnung, the archive, the export and the integrity check treat an
  external invoice like an issued one. An e-mail attaches the original files byte for byte.
- The rule "teaching stands alone" does not apply, since the invoice exists already; EN 16931 and the two tax numbers
  still hold.
- A year by hand issues no invoice from the app, and the app's own counter keeps its rule: no gap, no duplicate.
