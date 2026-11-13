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
- **E-invoice**: Mustang Project (ZUGFeRD, XRechnung), KoSIT validator, veraPDF
- **PDF**: Thymeleaf HTML rendered by Flying Saucer as PDF/A-3B
- **Database**: PostgreSQL with Flyway
- **UI**: Thymeleaf, htmx, Alpine.js, Tailwind CSS 4, daisyUI 5, Lucide icons. German only
- **Security**: Spring Security with passkeys (WebAuthn) and password login, roles `USER` and `ADMIN`
- **Tests**: JUnit 5, jqwik, PIT, Testcontainers, GreenMail, Playwright Test (TypeScript) in `e2e/`

## Common Commands

The code arrives with milestone M0. Commands follow then.

## Code Style

- Conventional Commits without scope, signed commits, squash merge.
- No em-dashes in code, comments, docs or commit messages. American spelling.
- Amounts in `BigDecimal`, never `double`. Dates as `LocalDate`, time from an injected `Clock`.

## Review Focus

Flag these in a pull request:

- Real names, addresses, tax numbers, IBANs or invoices in code, tests, fixtures or docs
- A change to an issued invoice, its number or its documents. Issued invoices are immutable
- Invoice number assignment outside the locked transaction at issue, or a path that can leave a gap
- Tax rules that contradict `docs/PLAN.md`: categories, §19 threshold, EN 16931 codes, rule BR-O-12
- Threshold sums that use the invoice date instead of the payment date
- Money in `double` or `float`, rounding without an explicit `RoundingMode`
- Generated XML or PDF that skips the KoSIT or veraPDF validation
- A URL without a role check, a form without CSRF protection, or a secret in the log
- A frontend asset loaded from a CDN at runtime
- Missing tests for new behavior: unit, integration, E2E and visual baselines per `docs/PLAN.md`
- No `## [Unreleased]` entry in `CHANGELOG.md`, or README not updated for a behavior change
