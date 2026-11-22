# 1. Issue an invoice in one transaction

- Status: accepted
- Date: 2026-11-14
- Recorded in: [PLAN.md](../PLAN.md), section 6 "Invoice status", item 2, and section 7 "Invoice numbers", item 4

## Context and problem

An invoice number must be unique and the numbers of a year must have no gap (§14 Abs. 4 Nr. 4 UStG, GoBD). An invoice
must not leave the app without a passed validation of its format. If the number were assigned first and the PDF and
XML validated afterward, a failed validation would either leave a gap or need a number that was already shown.

## Decision

`IssueService.issue` does everything in one database transaction: it locks the counter row of the year
(`NumberService.assign`), assigns the next number, writes the snapshot of seller and buyer, renders PDF and XML,
validates them (Mustang and veraPDF for ZUGFeRD, KoSIT for XRechnung), stores the sealed documents and sets the
status `ISSUED`. A failure at any step rolls back all of it, the number included. A Stornorechnung sets the status
of its invoice in the same transaction.

## Consequences

- No gap and no duplicate: the row lock serializes concurrent issues, and a rollback frees the number.
- The draft stays as it was after a failure, so the user can fix it and issue again.
- The transaction holds the lock while PDF and XML are rendered and validated. For one household this is acceptable.
- No number is reserved ahead: a dropped reservation would leave a gap.
