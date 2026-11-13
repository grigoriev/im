# Implementation plan

Invoice Manager (`im`) creates German e-invoices (e-Rechnung) for one freelancer (Freiberuflerin).
This plan records the decisions and the milestones up to production use on 2027-01-01.

## 1. Context

- The freelancer is a Kleinunternehmerin under §19 UStG.
- She has two kinds of income:
  - teaching, VAT-exempt under §4 Nr. 21 UStG;
  - other work, for example books, under §19 UStG.
- Customers are mostly German B2B companies. Some customers are abroad.
- Volume is 3 to 10 invoices per month.
- She files no VAT return. The income tax return (Anlage EÜR) is done in WISO Steuer.
- Production use starts on 2027-01-01. Until then: development, preparation, testing.
- The repository is public. It must never contain real customer or invoice data.

## 2. Legal requirements

Sources are listed in section 13. Verify each item again before the go-live.

1. All German businesses must be able to receive e-invoices since 2025-01-01.
2. A Kleinunternehmer has no duty to issue e-invoices. Customers can still ask for them.
3. An e-invoice is structured XML that conforms to EN 16931. A plain PDF is not an e-invoice.
4. Supported formats:
   - ZUGFeRD 2.x / Factur-X, profile EN16931 or XRECHNUNG: PDF/A-3 with embedded CII XML. Default for B2B.
   - XRechnung (UBL or CII): XML only. Needed for public-sector buyers, with a Leitweg-ID.
5. Each invoice carries the mandatory fields of §14 UStG and a unique, sequential number.
6. An issued invoice never changes. A correction is a cancellation invoice (Stornorechnung).
7. Retention: 8 years, immutable, in the original format (GoBD).

### Kleinunternehmer threshold (§19 UStG, as of 2025)

1. Revenue of the previous year must be at most 25 000 EUR.
2. Revenue of the current year must be at most 100 000 EUR.
3. Revenue counts by payment received, not by invoice date.
4. Above 100 000 EUR in the current year, regular VAT applies from that transaction on.
5. Exempt revenue under §4 Nr. 11 to 29 does not count. Teaching under §4 Nr. 21 is in this range.
6. Revenue not taxable in Germany does not count, for example B2B services to foreign customers.

## 3. Principles

1. **Correct first.** No invoice leaves the system without a passed KoSIT validation.
2. **Immutable.** Issuing freezes the invoice, its XML and its PDF. The system stores a SHA-256 of each document.
3. **Snapshot.** Issuing copies seller and buyer data into the invoice. Later edits of master data do not change it.
4. **Small footprint.** One household, two users, one container plus a database.
5. **Own data.** Full export and import. No dependency on a cloud service.
6. **Simple UI.** German only. Reuse customers, line templates and earlier invoices.
7. **No real data in git.** Tests and demo use generated data only.

## 4. Technology

| Layer | Choice | Reason |
|---|---|---|
| Language | Java 25 (LTS) | Main stack of the developer. Best e-invoice libraries are on the JVM |
| Framework | Spring Boot 4.x, Maven | Security, mail, scheduling, Flyway out of the box |
| e-invoice | Mustang Project | Creates and reads ZUGFeRD, Factur-X and XRechnung |
| Validation | KoSIT Validator + XRechnung configuration | Official validator, the same check that buyers run |
| PDF layout | Thymeleaf HTML, rendered by Flying Saucer (OpenPDF) | Own layout. Mustang then embeds the XML into PDF/A-3 |
| Database | PostgreSQL + Flyway | Robust, simple `pg_dump` backups |
| UI | Thymeleaf + htmx | Server-side CRUD. No SPA build for 10 invoices per month |
| Security | Spring Security, form login, Argon2 password hashes | Two users, two roles |
| Tests | JUnit 5, Testcontainers, AssertJ | Real PostgreSQL in tests |
| Packaging | Container image on GHCR, multi-arch, attested | Same pattern as sb-stack |
| Quality | SonarCloud, Renovate, CodeRabbit, Claude review | Standard for public repositories |

Group ID: `io.github.grigoriev`, artifact `im`, base package `io.github.grigoriev.im`.

Risk to check in milestone M0: Flying Saucer output must convert to valid PDF/A-3.
Fallback: the PDF visualization of Mustang (XSL-FO).

## 5. Users and roles

| Role | Who | Rights |
|---|---|---|
| `USER` | the freelancer | Customers, invoices, sending, payments, reports |
| `ADMIN` | the developer | Everything of `USER`, plus settings, users, audit log, backup status |

1. The first admin account comes from environment variables at the first start.
2. The admin creates the user account in the admin panel.
3. Sessions use secure cookies and CSRF protection. A "remember me" option is available.
4. nginx additionally allows only the home LAN (10.10.10.0/24).

## 6. Domain model

| Entity | Purpose |
|---|---|
| `AppUser` | Login, role, password hash |
| `SellerProfile` | Name, address, tax number, IBAN, contact data, logo |
| `Customer` | Address, country, e-mail, USt-IdNr, Leitweg-ID, preferred format, default tax category |
| `LineTemplate` | Reusable invoice line: text, unit, price, tax category |
| `Invoice` | Number, status, dates, service period, payment terms, snapshots of seller and buyer |
| `InvoiceLine` | Text, quantity, unit, price, tax category |
| `Document` | Generated PDF or XML: type, path, SHA-256, size |
| `Dispatch` | When, to whom, channel (`EMAIL_APP` or `MANUAL`), by which user, note |
| `Payment` | Date received, amount, note |
| `NumberSequence` | Format pattern, year, last number |
| `ThresholdBaseline` | Revenue counted for §19 in a year before the system existed |
| `Setting` | Key-value settings: SMTP, texts, defaults |
| `AuditEvent` | Who did what and when |

### Invoice status

```text
DRAFT -> ISSUED -> SENT -> PAID
           |         |
           +---------+--> CANCELLED (by a Stornorechnung)
```

1. `DRAFT` can change or be deleted. It has no number.
2. `ISSUED` gets the next number, the snapshots and the frozen documents.
3. `SENT` records each dispatch. One invoice can have several dispatches.
4. `PAID` needs at least one payment that covers the total.
5. `CANCELLED` links to the Stornorechnung. The cancellation invoice has its own number.

### Tax categories

| Category | Invoice text (draft, verify) | EN 16931 code | Counts for §19 |
|---|---|---|---|
| `KLEINUNTERNEHMER` | Gemäß § 19 UStG wird keine Umsatzsteuer berechnet. | E | yes |
| `EXEMPT_4_21` | Steuerfrei gemäß § 4 Nr. 21 UStG. | E | no |
| `REVERSE_CHARGE_EU` | Steuerschuldnerschaft des Leistungsempfängers. | AE | no |
| `NOT_TAXABLE_ABROAD` | Nicht im Inland steuerbare Leistung. | O | no |
| `STANDARD_7`, `STANDARD_19` | Later, after the Kleinunternehmer status ends | S | n/a |

The category is set per line. One invoice can mix categories.
The model includes the standard rates now, so the end of the Kleinunternehmer status needs no redesign.

## 7. Features

### Invoice numbers

1. The admin sets the pattern, for example `{YYYY}-{NN}`, which gives `2026-01`.
2. Tokens: `{YYYY}`, `{YY}`, `{N}` with padding (`{NN}`, `{NNN}`), fixed text.
3. The counter resets each year. The admin can set the start value.
4. A number is assigned only at issue, in one transaction with a row lock. No gaps, no duplicates.
5. A pattern change applies to the next number. The system rejects a pattern that can repeat an existing number.

### Sending

1. Send from the app: SMTP (Fastmail) with the PDF, the XML, or both, per customer default.
2. The admin configures SMTP and the e-mail template in the admin panel.
3. Mark as sent by hand: date, recipient, optional note. This covers sending from her own mail program.
4. Each dispatch shows in the invoice history.

### Payments

1. Record a payment: date and amount. Partial payments are allowed.
2. Overview of open and overdue invoices.

### Threshold tracking

1. Dashboard: counted §19 revenue of the previous and the current year against 25 000 and 100 000 EUR.
2. Forecast at the current rate.
3. Warning when a new draft would push the current year over the limit.
4. The 2026 revenue goes into `ThresholdBaseline`, because the system starts in 2027.

### Reports and export

1. Yearly report for Anlage EÜR: income by payment date (Zuflussprinzip), split by tax category.
2. CSV of all payments of a year.
3. Archive export: ZIP with all XML and PDF files of a year.
4. Full export and import: ZIP with JSON data and all documents. This also moves the system to a new host.

## 8. Deployment on lemming

1. CI builds the image and pushes it to `ghcr.io/grigoriev/im` with build attestations.
2. The compose file lives in the intranet repository: `lemming/docker/im`.
3. Services: `im` (app) and `im-db` (PostgreSQL). The app listens on `127.0.0.1` only.
4. The host nginx proxies `im.h.g7v.io` to the app and allows the home LAN only.
5. The certificate comes from acme.sh, as for the other sites.
6. Data lives under `/volume1/docker/im`: database files and the document store.
7. Renovate and the nightly `update.sh` update the image. Flyway migrates the schema at start.
8. Secrets (database password, SMTP password, first admin) stay outside git, in files with mode 600.

## 9. Backups

1. Nightly: `pg_dump` and the document store to `/volume1/backup/im`.
2. Nightly: restic copies that directory, encrypted, to meerkat.
3. Retention: 7 daily, 8 weekly, 24 monthly snapshots. Yearly archives stay for 10 years.
4. Monthly: an automatic restore test into a throwaway container. A failure sends an e-mail.
5. The admin panel shows the time of the last successful backup.

## 10. Testing

1. Unit tests for the number generator, tax rules and threshold calculation.
2. Every generated invoice passes KoSIT validation in the test suite.
3. Golden files for XML output, one per tax category and format.
4. Integration tests with Testcontainers (PostgreSQL) and a fake SMTP server.
5. Coverage gate in SonarCloud.
6. Before the go-live: send test invoices to free online validators and to a test recipient.

## 11. Milestones

| # | Milestone | Content | Target |
|---|---|---|---|
| M0 | Skeleton | Maven project, Spring Boot, CI, image on GHCR, Sonar, Renovate, CodeRabbit. PDF/A-3 spike | 2026-11 |
| M1 | Base | Login and roles, seller profile, customers, settings, audit log | 2026-11 |
| M2 | Invoices | Drafts, issue, numbers, PDF, ZUGFeRD, XRechnung, KoSIT, archive | 2026-12 |
| M3 | Sending and payments | SMTP sending, manual dispatch, payments, Stornorechnung | 2026-12 |
| M4 | Threshold and reports | Dashboard, baseline, EÜR report, CSV, archive export | 2026-12 |
| M5 | Operations | Deployment on lemming, backups to meerkat, restore test, full export and import | 2026-12 |
| M6 | Acceptance | Test with realistic invoices, fixes, final legal check | 2026-12 |
| GO | Go-live | Clean database, real seller profile, baseline 2026, first invoice `2027-01` | 2027-01-01 |

## 12. Open questions

1. Does the Goethe-Institut need XRechnung and a Leitweg-ID? Ask the contact person.
2. Foreign customers: invoice texts, USt-IdNr, Zusammenfassende Meldung. Verify before M6.
3. Exact mapping of the yearly report to the lines of Anlage EÜR.
4. Final wording of the tax notes on the invoice.
5. Currency: EUR only, or also foreign currencies?

## 13. Sources

- [e-Rechnung Bund: buyer reference (Leitweg-ID)](https://e-rechnung-bund.de/en/faq_category/buyer-reference/)
- [§19 UStG, Haufe](https://www.haufe.de/id/norm/umsatzsteuergesetz-19-besteuerung-der-kleinunternehmer-HI847581_p19.html)
- [Kleinunternehmerregelung, Handelskammer Hamburg](https://www.handelskammer-hamburg.de/recht-steuern/steuerrecht/umsatzsteuer-mehrwertsteuer/umsatzsteuer-mehrwertsteuer-national/kleinunternehmerregelung-6680502)
- [Mustang Project](https://www.mustangproject.org/)
- [KoSIT Validator](https://github.com/itplr-kosit/validator)
