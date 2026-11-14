# CLAUDE.md

This file guides Claude Code when it works on or reviews this repository.

## Project Overview

Invoice Manager (`im`) creates German e-invoices for one freelancer, a Kleinunternehmerin under
§19 UStG with VAT-exempt teaching under §4 Nr. 21 UStG. It runs as a container on a home NAS,
reachable from the home network only. The plan with all decisions is in `docs/PLAN.md`.
The UI mockup is in `docs/mockup`.

The repository is public. It must never contain real customer, invoice or personal data.

## Tech Stack

- **Backend**: Java 25, Spring Boot 4, Maven
- **E-invoice**: Mustang Project (ZUGFeRD, XRechnung). Validation: Mustang validator with veraPDF for ZUGFeRD, KoSIT for XRechnung
- **PDF**: Thymeleaf HTML rendered by Flying Saucer as PDF/A-3B
- **Database**: PostgreSQL with Flyway
- **UI**: Thymeleaf, htmx, Alpine.js, Tailwind CSS 4, daisyUI 5, Lucide icons. German only
- **Security**: Spring Security with passkeys (WebAuthn) and password login, roles `USER` and `ADMIN`
- **Tests**: JUnit 5, PIT, Testcontainers, GreenMail, Playwright Test (TypeScript) in `e2e/`

## Common Commands

```sh
mvn verify                                   # unit tests (100 % coverage gate), integration tests, PIT
mvn test -Dtest=PdfVisualTest -Dvisual.update=true   # write new PDF baselines, commit them on purpose
mvn package -DskipTests && scripts/e2e.sh    # E2E and visual tests in Docker against the image
scripts/e2e.sh --update                      # write new screenshot baselines
```

Maven installs Node and builds the CSS (`npm run build`) by itself. Java 25 is required.

## Architecture

Package `io.github.grigoriev.im`:

- `einvoice`: the issued invoice (`InvoiceDocument`, `InvoiceLine`, `Party`, `TaxCategory`, `InvoiceFormat`),
  the PDF/A-3B layout (`InvoicePdfRenderer`, template `templates/pdf/invoice.html`), the Mustang mapping
  and export (`MustangInvoiceMapper`, `EInvoiceWriter`), the validators (`ZugferdValidator`: Mustang,
  `XRechnungValidator`: KoSIT) and `EInvoiceService`, which issues and validates per format
- `user`: accounts, roles (`USER`, `ADMIN`), Argon2 passwords, the first admin from `IM_ADMIN_*`,
  passkeys (`PasskeyService` on the JDBC stores of Spring Security, `IM_WEBAUTHN_*`)
- `seller`: the seller profile (`SellerService`, one row, IBAN checksum) and the default tax notes (`TaxNoteService`)
- `audit`: the audit log (`AuditService.add`, joins the caller's transaction; with an invoice ID the entry joins its `history`); logins come from `user.LoginAudit`,
  new passkeys from `user.AuditedCredentialRepository`. A new change of data records an `AuditAction`
- `invoice`: invoices (`Invoice` with `DraftLine`s); `InvoiceService` creates and saves drafts and names what blocks the issue
  (`problems`), `document` builds the `InvoiceDocument` of a complete draft; `IssueService.issue` assigns the number,
  validates and freezes in one transaction; `DispatchService` records a dispatch and moves the status to `SENT`; `PaymentService` records payments and refunds and keeps `PAID` in step with their sum; `DocumentStore` keeps the PDF and XML (`invoice_document`) and the JSON snapshot; `TemplateService` keeps the templates
  per customer. `LineForm.toLine` reads and checks a line of both editors (fragment `fragments/lines.html`)
- `customer`: the customers (`CustomerService`), each with its e-invoice format and `toParty()` for the buyer
- `country`: the ISO countries with German names from Java, and the short list of the admin (`CountryService`)
- `validation`: `Fields`, the shared field checks of the forms (text, e-mail, VAT ID, country, length)
- `numbering`: the number pattern (`NumberPattern`) and the yearly counter (`NumberService.assign` under a row lock)
- `config`: `SecurityConfig` (form login, remember-me, CSRF, CSP, admin area), `ClockConfig`
- `web`: controllers and Thymeleaf pages (`IssuedInvoicePage` fills the page of an issued invoice, `InvoiceRecordsController` takes its dispatches and payments), styled with Tailwind and daisyUI (`src/main/frontend/app.css`).
  Every page uses the shell of `templates/fragments/layout.html`; the admin pages live under `/admin`

Tests: unit tests `*Test`, integration tests `*IT` with PostgreSQL in Testcontainers, PDF baselines in
`src/test/resources/visual/pdf`, Playwright tests and screenshot baselines in `e2e/tests`.
`AccessRulesIT` holds the access rule of every URL: a new controller URL needs an entry there, or it fails.

## Code Style

- Conventional Commits without scope, signed commits, squash merge.
- No em-dashes in code, comments, docs or commit messages. American spelling.
- Amounts in `BigDecimal`, never `double`. Dates as `LocalDate`, time from an injected `Clock`.

## Reviews

Claude Code and Greptile review every pull request. Neither reviews Renovate: the Claude workflow skips
bots, and the Author/Exclude filter in the Greptile dashboard excludes them. The CI checks still guard
every Renovate update.

## Review Focus

Flag these in a pull request:

- Real names, addresses, tax numbers, IBANs or invoices in code, tests, fixtures or docs
- A change to an issued invoice, its number or its documents. Issued invoices are immutable
- Invoice number assignment outside the locked transaction at issue, or a path that can leave a gap
- Tax rules that contradict `docs/PLAN.md`: categories, §19 threshold, EN 16931 codes, rules BR-O-12 and BR-E-08
- Threshold sums that use the invoice date instead of the payment date
- Money in `double` or `float`, rounding without an explicit `RoundingMode`
- Generated XML or PDF that skips the validation of its format or profile, or checks a ZUGFeRD EN16931 profile against the XRechnung rules
- A URL without a role check, a form without CSRF protection, or a secret in the log
- A frontend asset loaded from a CDN at runtime
- A table or layout that scrolls sideways, or columns that depend on the window width instead of the card width
- Missing tests for new behavior: unit, integration, E2E and visual baselines per `docs/PLAN.md`
- No `## [Unreleased]` entry in `CHANGELOG.md`, or README not updated for a behavior change
