# Architecture decision records

Short records of lasting decisions, in the style of [MADR](https://adr.github.io/madr/). The plan in
[docs/PLAN.md](../PLAN.md) links each record where it states the decision. Records 1 to 6 were written after the
fact; their date is the date of the decision.

| # | Decision | Date |
|---|---|---|
| [0001](0001-issue-in-one-transaction.md) | Issue an invoice in one transaction: number, validation, freeze | 2026-11-14 |
| [0002](0002-write-once-by-triggers-and-an-app-role.md) | Write once by database triggers and a separate app role | 2026-11-15 |
| [0003](0003-hmac-seals-with-an-integrity-key.md) | HMAC seals with a key outside the database | 2026-11-15 |
| [0004](0004-import-only-into-an-empty-installation.md) | The full import goes only into an empty installation | 2026-11-14 |
| [0005](0005-external-invoices-and-numbers-by-hand.md) | External invoices and numbers by hand | 2026-11-22 |
| [0006](0006-english-technical-names-and-no-fqcn.md) | English technical names and no fully qualified class names | 2026-11-14 |
| [0007](0007-api-tokens-and-mcp.md) | A REST API and an MCP server behind personal API tokens | 2026-11-24 |

A new record takes the next number, the status "accepted" and the date of the decision. A record that a later one
replaces gets the status "superseded by" with a link.
