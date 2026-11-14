# Changelog

All notable changes to this project are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-11-14

### Changed

- `scripts/e2e.sh` refreshes the timestamp of the jar before the image build: with the fixed timestamp of the reproducible build, Docker Desktop could build the image with an old jar.
- The check of an e-mail address runs in linear time, also for a long crafted input.
- JetBrains Mono replaces Inter in the app and in the invoice PDF; the PDF embeds the variant without code ligatures. Floating labels stay on one line and never cover their field.

### Added

- Datensicherung: the admin downloads one ZIP with all data and documents (users with password hashes and passkeys, settings, customers, templates, invoices with PDF and XML, dispatches, payments, audit log) and reads it into an empty installation of the same version, in one transaction, to move to a new host. The import keeps the triggers that freeze issued invoices and continues the identities; afterward every session ends, in every browser, and the users of the export log in. The import locks its tables until it commits and refuses a ZIP that expands beyond 512 MB or 100 000 entries.
- Archive: "Archiv ZIP" on "Berichte" downloads the stored PDF and XML of every invoice with an invoice date in the year, byte for byte, with "Inhalt.csv" listing number, date, customer, file, size and SHA-256. Cancelled invoices and Stornorechnungen are in it; drafts are not. Two numbers with the same file name stay apart. The year picker offers every year with payments or issued invoices.
- Berichte: the income of a year by payment received, with Zeile 12 (Kennzahl 111) and Zeile 13 (Kennzahl 119) of the Anlage EÜR 2025, the income per tax category, the part that counts for §19 and the list of payments. "CSV" downloads the payments for a spreadsheet: semicolons, German numbers, UTF-8, formulas neutralized. Each payment is split to the cent, so its categories add up to it and report, CSV and threshold agree. Income with VAT stays out of Zeilen 12 and 13; the report names it for Zeilen 15 and 17.
- Übersicht: open and overdue invoices, the money received this year, and the Kleinunternehmergrenze: the counted §19 revenue of last year against 25 000 €, of this year against 100 000 €, the forecast for next year and what does not count. A payment counts with the share of the §19 lines of its invoice, by payment date; a refund subtracts. The admin enters the revenue of years before the app on "Vorjahre"; a year with payments in the app is refused, so nothing counts twice. The draft editor warns when an invoice would cross 100 000 €.
- Stornieren: "Stornieren…" on an issued invoice creates its Stornorechnung as a draft with the same customer, format, service period and lines; the lines stay locked. Issuing it gives it its own number and sets the invoice to "Storniert" in the same transaction. One invoice has one Stornorechnung. A refund goes on the cancelled invoice as a payment with minus; the Stornorechnung takes no payments. The list marks it.
- Stornorechnung as an e-invoice: a credit note (type 381) with the lines of the cancelled invoice, which it names (BT-25, BT-26). The PDF says "Stornorechnung", shows "Storno zu" with number and date, and asks for no payment. Mustang and KoSIT validate it in every format.
- Sending by e-mail: "Per E-Mail senden" sends an issued invoice over SMTP with its stored PDF, XML or both, byte for byte, to the buyer's address with the e-mail text of its template or the default. A sent e-mail records a dispatch "E-Mail aus der App"; a failed one records nothing and keeps the typed form. The form posts once. The server comes from `IM_SMTP_*` and `IM_MAIL_*`; without it the page offers the manual dispatch only.
- E-mail text: the admin sets the default subject and text of the e-mail with an invoice on "E-Mail", with the placeholders of the invoice texts. An invoice remembers the template it came from, whose e-mail subject and text override the default field by field.
- Payments: "Zahlung erfassen" records the day the money came in, the amount (German style, the open amount by default) and a note. A negative amount is a refund or a correction. The invoice is "Bezahlt" while the payments cover the total, and goes back to "Versendet" or "Ausgestellt" after a refund. A cancelled invoice takes refunds only. Payments are written once and show in the history and in "Zahlungen" with the paid and open amount.
- Dispatch by hand: "Als versendet markieren" records the day, the way (own mail program, post, portal, in person), the recipient and a note. The first dispatch moves an issued invoice to "Versendet"; every dispatch shows in its history, with its note. The history shows the detail of each entry.
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
