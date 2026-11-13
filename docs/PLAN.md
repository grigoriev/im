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

1. **Correct first.** No invoice leaves the system without a passed validation for its format (see section 4).
2. **Immutable.** Issuing freezes the invoice, its XML and its PDF. The system stores a SHA-256 of each document.
3. **Snapshot.** Issuing copies seller and buyer data into the invoice. Later edits of master data do not change it.
4. **Small footprint.** One household, two users, one container plus a database.
5. **Own data.** Full export and import. No dependency on a cloud service.
6. **Simple UI.** German only. Reuse customers, line templates and earlier invoices.
   No horizontal scroll at any width: tables adapt to the width of their card (container queries),
   merge secondary columns, and turn into cards on a narrow screen. Text wraps instead of being cut.
7. **No real data in git.** Tests and demo use generated data only.

## 4. Technology

| Layer | Choice | Reason |
|---|---|---|
| Language | Java 25 (LTS) | Main stack of the developer. Best e-invoice libraries are on the JVM |
| Framework | Spring Boot 4.x, Maven | Security, mail, scheduling, Flyway out of the box |
| e-invoice | Mustang Project | Creates and reads ZUGFeRD, Factur-X and XRechnung |
| Validation, ZUGFeRD EN16931 | Mustang validator: EN 16931 and ZUGFeRD schematron, PDF/A-3 through veraPDF | The XRechnung rules do not apply to a plain EN16931 profile |
| Validation, ZUGFeRD XRECHNUNG | As EN16931, plus KoSIT with the XRechnung configuration on the embedded XML | The profile claims the XRechnung rules, for example BR-DE-15 |
| Validation, XRechnung | KoSIT validator + XRechnung configuration | Official validator, the same check that public buyers run |
| PDF layout | Thymeleaf HTML, rendered by Flying Saucer as PDF/A-3B | Own layout. Mustang then embeds the XML |
| Database | PostgreSQL + Flyway | Robust, simple `pg_dump` backups |
| UI | Thymeleaf, htmx, Alpine.js, Tailwind CSS 4, daisyUI 5, Lucide icons | Server-side CRUD. CSS classes only, no SPA |
| Design | Light and dark theme, Inter font in app and PDF, German formats | Mac and iPhone |
| Security | Spring Security: passkeys (WebAuthn) and password | Two users, two roles. Works without internet |
| Tests | JUnit 5, PIT, Testcontainers, GreenMail, Playwright (TypeScript) | See section 10 |
| Packaging | Container image on GHCR, linux/amd64, attested | The NAS is x86_64. Same pattern as sb-stack |
| Quality | SonarCloud, Renovate, Claude review, Greptile | Standard for public repositories |

Group ID: `io.github.grigoriev`, artifact `im`, base package `io.github.grigoriev.im`.

Flying Saucer supports PDF/A-3B (`PdfAConformance.PDF_A_3B`). All fonts must be embedded.
Milestone M0 checks the full chain: Flying Saucer PDF, Mustang embedding, then the validation of each format.

Currency: EUR only.

## 5. Users and roles

| Role | Who | Rights |
|---|---|---|
| `USER` | the freelancer | Customers, invoices, sending, payments, reports |
| `ADMIN` | the developer | Everything of `USER`, plus settings, users, audit log, backup status |

1. The first admin account comes from environment variables at the first start.
2. The admin creates the user account in the admin panel.
3. A new account logs in with a password first.
4. After login, the user links a passkey to the existing account: Face ID or Touch ID, synced by iCloud Keychain.
5. The profile page lists the passkeys of the user. The user adds, renames and removes them.
6. One account can have several passkeys, for example Mac and iPhone.
7. The login page offers the passkey first. The password stays as a fallback. Passwords use Argon2 hashes.
8. The admin can remove the passkeys of a user and reset the password. A reset ends every "Angemeldet bleiben" of the user.
9. Sessions use secure cookies and CSRF protection. A "remember me" option is available.
10. nginx additionally allows only the home LAN (10.10.10.0/24).

Not chosen:

- Sign in with Apple: needs a paid Apple Developer account and a domain that Apple can reach.
- Google login (OpenID Connect): possible as an add-on later. Login then depends on the internet.

## 6. Domain model

| Entity | Purpose |
|---|---|
| `AppUser` | Login, role, password hash |
| `Passkey` | WebAuthn credential of a user |
| `SellerProfile` | Name, address, tax number, IBAN, contact data, logo |
| `Customer` | Address, country, e-mail, USt-IdNr, buyer reference (the Leitweg-ID for a public buyer), format: ZUGFeRD EN16931 (default), ZUGFeRD XRECHNUNG or XRechnung |
| `InvoiceTemplate` | Per customer: title, texts, tax note, lines, payment terms, format (default from the customer), e-mail text |
| `Invoice` | Number, status, format, title, dates, service period, texts, tax note, payment terms, snapshots of seller and buyer |
| `InvoiceLine` | Text, quantity, unit, price, tax category |
| `Document` | Generated PDF or XML: type, path, SHA-256, size |
| `Dispatch` | When, to whom, channel (`EMAIL_APP` or `MANUAL`), by which user, note |
| `Payment` | Date, amount, note. A negative amount is a refund |
| `NumberSequence` | Format pattern, year, last number |
| `ThresholdBaseline` | Revenue counted for §19 in a year before the system existed |
| `Setting` | Key-value settings: SMTP, texts, defaults |
| `AuditEvent` | Who did what and when |

### Invoice status

```text
DRAFT -> ISSUED -> SENT -> PAID
           |         |       |
           +---------+-------+--> CANCELLED (by a Stornorechnung)
```

1. `DRAFT` can change or be deleted. It has no number.
2. `ISSUED` gets the next number, the snapshots and the frozen documents.
3. `SENT` records each dispatch. One invoice can have several dispatches.
4. `PAID` applies when the sum of all payments covers the total.
5. `CANCELLED` links to the Stornorechnung. The cancellation invoice has its own number.
6. A paid invoice can be cancelled too. The money that goes back is a refund: a payment with a negative amount.
7. A refund lowers the §19 revenue and the EÜR income in the year it is paid, not in the year of the invoice.

### Tax categories

| Category | Default tax note (verify) | EN 16931 code | Counts for §19 |
|---|---|---|---|
| `KLEINUNTERNEHMER` | Gemäß § 19 UStG wird keine Umsatzsteuer berechnet. | E | yes |
| `EXEMPT_4_21` | Steuerfrei gemäß § 4 Nr. 21 UStG. | E | no |
| `REVERSE_CHARGE_EU` | Steuerschuldnerschaft des Leistungsempfängers. | AE | no |
| `NOT_TAXABLE_ABROAD` | Nicht im Inland steuerbare Leistung. | O | no |
| `STANDARD_19`, `STANDARD_7` | none, the PDF shows the VAT per rate | S | n/a |

The category is set per line. One invoice can mix categories, with these rules:

1. `NOT_TAXABLE_ABROAD` (code O) never mixes with another category (EN 16931 rule BR-O-12). The editor rejects such a draft.
2. `KLEINUNTERNEHMER` and `EXEMPT_4_21` share code E at 0 %. EN 16931 allows one VAT breakdown per code (BR-E-08).
   A mixed invoice gets one E breakdown. Its exemption reason (BT-120) joins both tax notes. The PDF shows both notes.
3. The system keeps the category per line, so the §19 revenue counts only the `KLEINUNTERNEHMER` lines.
4. `REVERSE_CHARGE_EU` needs the VAT ID of seller and buyer on the invoice (§14a Abs. 1 UStG). The editor rejects a draft without them.
5. `KLEINUNTERNEHMER` never mixes with a standard rate: a Kleinunternehmer charges no VAT.
6. The VAT of a rate is computed once on the sum of its net line amounts and rounded half up (BR-CO-17).
   The PDF shows the net total, the VAT per rate with its base, and the gross total.
7. With more than one category, the PDF shows a tax column: each line names its rate or exemption (§14 Abs. 4 Nr. 8 UStG).
The standard rates serve every business that charges VAT, for example a shop that sells goods: the project is open source.
They also cover the end of the Kleinunternehmer status without a redesign.
The admin edits the default tax notes. A template and an invoice can override them.

## 7. Features

### Invoice templates

1. The user creates one or more templates per customer.
2. A template holds: title, intro text, closing text, tax note, lines, payment terms, format, e-mail subject and body.
3. Texts support placeholders, for example `{Leistungszeitraum}`, `{Zahlungsziel}`, `{Rechnungsnummer}`.
4. A new invoice starts from a template. The template content, the format included, is copied into the draft.
5. The user edits every field of the draft: texts, tax note, lines, dates.
6. A preview shows the final PDF before issue.
7. A template change does not affect existing invoices.
8. A line is a lump sum by default: the PDF shows the text and the amount only. The scope of the service goes into the text,
   for example "Lesung am 16.03.2027, 90 Min." (§14 Abs. 4 Nr. 5 UStG). A line can carry a quantity instead.
   Then the PDF shows quantity, unit price and amount. The quantity and unit price columns appear only if a line has a quantity.
   The user never picks a unit: the XML carries `LS` for a lump sum and `C62` for a quantity, as EN 16931 needs one.
   Switching a line from quantity to lump sum keeps its amount.
9. Validation before issue: a VAT-exempt line needs a tax note. Both XRechnung formats need a buyer reference (BR-DE-15).
   The validation of the format must pass.

### Invoice numbers

1. The admin sets the pattern, for example `{YYYY}-{NN}`, which gives `2026-01`.
2. Tokens: `{YYYY}`, `{YY}`, `{N}` with padding (`{NN}`, `{NNN}`), fixed text.
3. The counter resets each year. The admin can set the start value only for a year without issued invoices.
   The check runs in the same locked transaction that assigns numbers.
4. A number is assigned only at issue, in one transaction with a row lock. No gaps, no duplicates.
5. A pattern change applies to the next number. The system rejects a pattern that can repeat an existing number.

### Sending

1. Send from the app: SMTP (Fastmail) with the PDF, the XML, or both, per customer default.
2. The admin configures SMTP and the e-mail template in the admin panel.
3. Mark as sent by hand: date, recipient, optional note. This covers sending from her own mail program.
4. Each dispatch shows in the invoice history.

### Payments

1. Record a payment: date and amount. Partial payments are allowed.
2. Record a refund after a Stornorechnung: a payment with a negative amount.
3. Overview of open and overdue invoices.

### Threshold tracking

1. Dashboard: counted §19 revenue of the previous and the current year against 25 000 and 100 000 EUR.
2. Forecast at the current rate.
3. Warning when a new draft would push the current year over the limit.
4. The 2026 revenue goes into `ThresholdBaseline`, because the system starts in 2027.

### Reports and export

1. Yearly report for Anlage EÜR: income by payment date (Zuflussprinzip).
   It shows the sums for the EÜR form fields, ready to type into WISO Steuer.
   Example: total Kleinunternehmer income, and the part excluded from the §19 threshold.
2. CSV of all payments of a year.
3. Archive export: ZIP with all XML and PDF files of a year.
4. Full export and import: ZIP with JSON data and all documents. This also moves the system to a new host.

## 8. Deployment on lemming

1. CI builds the image for linux/amd64 (the NAS is x86_64) and pushes it to `ghcr.io/grigoriev/im` with build attestations.
2. The compose file lives in the intranet repository: `lemming/docker/im`.
3. Services: `im` (app) and `im-db` (PostgreSQL). The app listens on `127.0.0.1` only.
4. The host nginx proxies `im.h.g7v.io` to the app and allows the home LAN only. It sends `X-Forwarded-Proto`,
   so the app marks its cookies `Secure` (`server.forward-headers-strategy=native`).
   Passkeys are bound to that host: `IM_WEBAUTHN_RP_ID=im.h.g7v.io`, `IM_WEBAUTHN_ALLOWED_ORIGINS=https://im.h.g7v.io`.
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

Every feature ships with unit tests, integration tests where relevant, an E2E scenario and visual baselines.

### Levels

| Level | Scope | Tools | Runs |
|---|---|---|---|
| Unit | Domain rules: numbers, tax categories, §19 threshold, amounts, template placeholders | JUnit 5, AssertJ | `mvn test` |
| Mutation | Domain core: tax rules, threshold, numbers, amounts | PIT | `mvn verify`, gate in CI |
| Integration | Spring context, database, security, mail, e-invoice formats | Spring Boot Test, Testcontainers, GreenMail | `mvn verify` (`*IT`) |
| E2E | User flows in the browser against the built image | Playwright Test (TypeScript) in `e2e/` | CI job |
| Visual, UI | Screenshots of screens and states | Playwright `toHaveScreenshot` | CI job, with E2E |
| Visual, PDF | Invoice layout | PDFBox renders pages to PNG, compared with baselines | `mvn verify` |

### Integration suite

1. E-invoice chain: Flying Saucer, Mustang, the validation of the format, then Mustang reads the file back. One case per tax category, per format and per mix of categories.
2. Database: Flyway on an empty database. After the go-live also upgrades from the last release with data.
3. Security: access to each URL for `USER`, `ADMIN` and anonymous. CSRF. Password login.
4. Passkeys: registration and login with the Chromium virtual authenticator in Playwright.
5. Numbers under load: parallel issue gives no duplicates and no gaps.
6. Mail: GreenMail checks recipient, subject, attachments and the dispatch record.
7. Export and import: a full ZIP into an empty system gives the same data and the same SHA-256 values.
8. Backup and restore: a `pg_dump` restored into a new container. The app starts and the data is complete.
9. Image smoke test: the built image with its database starts through compose. Health and login work.

### Visual tests

1. A fixed `Clock`, generated data and embedded fonts make the output deterministic.
2. Screenshots come only from the pinned Playwright Linux image. A script runs the same image locally.
3. UI baselines: each new screen and each relevant state (empty, filled, validation error, threshold warning).
4. PDF baselines: one line, page break, mixed categories, Stornorechnung, XRechnung with Leitweg-ID.
5. On failure, CI uploads the diff images as an artifact.
6. A separate command updates the baselines. Commit the new baselines on purpose.
7. Playwright also runs axe accessibility checks.
8. Playwright checks every view at 375, 768, 1024, 1440 and 1920 px: no element scrolls sideways or overflows its card.

### Gates

1. Unit test coverage 100 %, lines and branches, measured by JaCoCo and enforced in the build.
   Integration tests add to the report, but do not count toward the 100 %.
2. PIT mutation score of the domain core at least 80 %.
3. SonarCloud quality gate green. No open findings from any review: Claude, Greptile, Sonar.

### Definition of done

1. Unit tests for the logic.
2. Integration test if the feature touches the database, security, mail or a format.
3. E2E scenario for the user flow.
4. Visual baselines for new screens and states, and for the PDF if its layout changed.
5. All gates green and every review finding fixed.

### Before the go-live

1. Send test invoices to free online validators.
2. Send test invoices to a test recipient.

## 11. Dependency updates (Renovate)

The repository extends the shared preset `github>grigoriev/renovate-config`.
Every dependency lives in a file that Renovate reads. No version hides in code.
Renovate updates everything by itself (owner decision 2026-11-13): an update merges once CI is green.

| Dependency | Where | Manager |
|---|---|---|
| Spring Boot, Mustang, veraPDF, KoSIT, Maven plugins, test libraries | `pom.xml` | maven |
| Tailwind, daisyUI, htmx, Alpine.js, Lucide, Inter | `package.json` | npm |
| Playwright and its image, as one group | `e2e/package.json`, `e2e/docker-compose.yml` | npm, docker-compose |
| Node for the CSS build, as one group | `.node-version`, `pom.xml` | nodenv, regex |
| Java base image, tag and digest | `Dockerfile` | dockerfile |
| PostgreSQL image of the tests, limited to the production major | one constant with a `// renovate:` comment | regex |
| XRechnung validator configuration | `pom.xml` property with a `renovate:` comment | regex, GitHub releases |
| GitHub Actions, SHA pinned | `.github/workflows` | github-actions |
| `im` image on lemming | intranet repository, compose file | docker |

### Rules

1. Automerge needs the full CI: lint, unit, integration, mutation, E2E and visual tests, Sonar.
2. The tests guard the legal output: every format and every mix of categories runs through Mustang and KoSIT.
3. A visual change after an update fails the build. The pull request stays open until the baselines are reviewed.
4. A red pull request stays open. It gets a fix in its own pull request.
5. A new XRechnung configuration needs the file name and SHA-256 in `pom.xml`. Renovate changes only the version.
6. The root `.npmrc` sets `allow-remote=all`: npm 12 otherwise refuses a bundled Tailwind package, and Renovate cannot update the lock file.
   `e2e/` has no such package and keeps the npm default.
7. The PostgreSQL image of the tests stays on the production major. A major upgrade runs first on lemming (dump and restore),
   then the limit in `renovate.json` moves in the same step.
8. The frontend libraries come from npm and go into the jar at build time. No CDN at runtime.

## 12. Milestones

| # | Milestone | Content | Target |
|---|---|---|---|
| M0 | Skeleton | Maven project, Spring Boot, CI, image on GHCR, Sonar, Renovate. PDF/A-3 spike | 2026-11 |
| M1 | Base | Login with passkeys, roles, seller profile, customers, settings, audit log | 2026-11 |
| M2 | Invoices | Templates, drafts, preview, issue, numbers, PDF, ZUGFeRD, XRechnung, validation, archive | 2026-12 |
| M3 | Sending and payments | SMTP sending, manual dispatch, payments, Stornorechnung | 2026-12 |
| M4 | Threshold and reports | Dashboard, baseline, EÜR report, CSV, archive export | 2026-12 |
| M5 | Operations | Deployment on lemming, backups to meerkat, restore test, full export and import | 2026-12 |
| M6 | Acceptance | Test with realistic invoices, fixes, final legal check | 2026-12 |
| GO | Go-live | Clean database, real seller profile, baseline 2026, first invoice `2027-01` | 2027-01-01 |

## 13. Open questions

1. Does the public-sector customer need XRechnung and a Leitweg-ID? Ask the contact person.
   XRechnung is in scope either way.
2. Foreign customers: invoice texts, USt-IdNr, Zusammenfassende Meldung. Verify before M6.
3. Field mapping of the yearly report: check against the Anlage EÜR 2027 form before M4.
4. Final wording of the default tax notes.

## 14. Sources

- [e-Rechnung Bund: buyer reference (Leitweg-ID)](https://e-rechnung-bund.de/en/faq_category/buyer-reference/)
- [§19 UStG, Haufe](https://www.haufe.de/id/norm/umsatzsteuergesetz-19-besteuerung-der-kleinunternehmer-HI847581_p19.html)
- [Kleinunternehmerregelung, Handelskammer Hamburg](https://www.handelskammer-hamburg.de/recht-steuern/steuerrecht/umsatzsteuer-mehrwertsteuer/umsatzsteuer-mehrwertsteuer-national/kleinunternehmerregelung-6680502)
- [Mustang Project](https://www.mustangproject.org/)
- [veraPDF](https://verapdf.org/)
- [KoSIT Validator](https://github.com/itplr-kosit/validator)
