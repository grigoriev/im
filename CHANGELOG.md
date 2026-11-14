# Changelog

All notable changes to this project are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- JetBrains Mono replaces Inter in the app and in the invoice PDF; the PDF embeds the variant without code ligatures. Floating labels stay on one line and never cover their field.

### Added

- Dispatch by hand: "Als versendet markieren" records the day, the way (own mail program, post, portal, in person), the recipient and a note. The first dispatch moves an issued invoice to "Versendet"; every dispatch shows in its history. The history shows the detail of each entry.
- Invoice page: an issued invoice shows its number, status, customer, total and due date, its history (draft created, issued, by whom and when), its documents and the checks it passed. The list filters by year, status and a part of the customer's name or the number.
- Issue: "Rechnung ausstellen" gives a complete draft its number, freezes it with seller and buyer, and stores the validated PDF and XML with their SHA-256 in the database. A failed validation keeps the draft and its number free. An issued invoice shows its data and downloads its documents; the database refuses any change. A number pattern whose next number exists is refused.
- Preview: a complete draft opens as the PDF it will be issued as, with "Entwurf" as number; the placeholders `{Rechnungsnummer}`, `{Rechnungsdatum}`, `{Leistungszeitraum}` and `{Zahlungsziel}` are filled in its texts.
- Templates per customer: name, format, payment days, texts with placeholders, lines and the e-mail; "Rechnung erstellen" starts a draft that copies the template, and a later change of the template leaves the draft as it is.
- Draft invoices: "Rechnungen" lists them; a new draft takes format, buyer reference and tax note from customer and profile; the editor changes dates, texts and lines (lump sum or with quantity, German amounts) and shows totals and what the issue still needs. A customer with invoices can no longer be deleted.
- Audit log: logins, failed logins (naming only existing users), passkeys, passwords, users, settings and customers, on the admin page "Protokoll".
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
