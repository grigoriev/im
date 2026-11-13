# Changelog

All notable changes to this project are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Countries: the forms offer a list of countries with German names; the admin keeps that list on "Länder" (only Germany at the start), and a record keeps a country the admin removed.
- Customers: list, create, change and delete, with the e-invoice format per customer; both XRechnung formats need a buyer reference (BR-DE-15) and an e-mail address.
- Invoice numbers: the admin sets the pattern (`{YYYY}-{NN}` by default) and the start value of a year without numbers; a number is assigned under a row lock, without gaps or duplicates.
- Seller profile: a line below the name, the website, the bank name and the account holder, all optional.
- Settings: the seller profile with address, contact, tax number or VAT ID, bank account and tax status, checked for what an invoice needs (IBAN checksum, XRechnung contact); the admin edits the default tax notes.
- Passkey login (Touch ID, Face ID): each user adds, renames and removes passkeys and changes the password on "Mein Konto"; the admin removes the passkeys of a user.
- Login with password (Argon2), "Angemeldet bleiben", roles user and admin, the first admin from the environment, and the admin page "Benutzer".
- App shell with sidebar menu, German error pages, a strict content security policy.
- Implementation plan (`docs/PLAN.md`) and clickable UI mockup (`docs/mockup`).
- Regular VAT at 19 % and 7 % next to the exempt categories, with net total, VAT per rate and gross total.
- Invoice lines are lump sums by default: the PDF shows text and amount, the quantity columns appear only when a line has a quantity.
- E-invoice chain: PDF/A-3B layout, ZUGFeRD (EN16931, XRECHNUNG) and XRechnung, validated with Mustang and KoSIT.
- Start page with light and dark theme, served with its own fonts and libraries.
- Container image, PostgreSQL with Flyway, and the release workflow to ghcr.io.
- Repository baseline: security policy, contribution guide, Claude Code review, Scorecard, Renovate.

### Fixed

- The start page no longer fails for a logged-in user without an account record.
