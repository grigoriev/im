# Changelog

All notable changes to this project are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- The name follows "Mit freundlichen Grüßen" on the next line, in the PDF and in the e-mail, without a paragraph between. The HTML e-mail puts the greeting with the name below the box with the facts, the address at its foot.
- The label of a field stays gray on focus and on a wrong value. Only the frame turns blue or red.
- The PDF puts the address into the field of DIN 5008, form B, so it shows in a window envelope. The information block starts at 50 mm on the right and also holds the VAT ID of the customer and the buyer reference. A foreign country stands as its German name in capitals.
- A last line on the PDF names the XML data as the invoice. A ZUGFeRD PDF says that it carries them; the PDF next to an XRechnung calls itself a reading copy, so it does not count as a second invoice.
- The PDF names the amount in the payment request, as the standard form of DATEV does.
- The PDF signs below the closing with the contact person, or the name without one, unless the closing ends with it already. The text part of the e-mail ends with the same signature as its HTML part.
- Profil, Kontakt: "Telefon auf der Rechnung zeigen" puts the phone number into the footer of the PDF. It is off by default; the XML carries the number anyway, as XRechnung needs it. An issued invoice keeps the choice it was issued with.

### Fixed

- The intro and the closing of the PDF keep their line breaks; before, a closing of two lines ran into one.

## [0.1.4] - 2026-11-15

### Added

- Dokumente: a store for the business documents next to the invoices, for example the Verfahrensdokumentation, Steuerbescheide, letters of the Finanzamt, certificates and contracts. A document has a kind, a title, a date, the year it concerns and a note, and a PDF, JPG or PNG of at most 25 MB. Its file never changes and never goes away: a new version replaces it in the list, and the old one stays in its history. The description may change. Every upload and change goes into the log with the SHA-256 of the file.
- The yearly archive has a folder "Dokumente" with every version of the documents of that year and a table of contents. A document belongs to the year it concerns, without one to the year of its date. The year picker on "Berichte" offers the years of the documents too.
- The full export and import and the nightly backup take the documents along.
- Verfahrensdokumentation: "Dokumente" writes it from the profile and four values (valid from, program for receipts and expenses, place of the yearly archives, change), with the version of the app and a table of every change. "Vorschau (PDF)" downloads it; "Als Fassung ablegen" keeps it as a new version of the document. Print it, sign it and keep the scan as the next version.
- Benutzer: the admin deletes a user with its passkeys and "Angemeldet bleiben". Its open sessions end in every browser. Nobody deletes the own account, and the last admin stays. The log keeps the user name in its entries and records the deletion.
- The e-mail with an invoice is HTML as well as text. The text stays as typed; the HTML shows it in paragraphs, adds a box with the invoice date, amount, due date, IBAN, BIC and reference, and the address of the seller below. A Stornorechnung names its invoice and asks for no payment. Mail programs show the HTML and fall back to the text.
- "Vorschau" next to "Senden" shows the e-mail as the customer sees it, in a new tab.

### Fixed

- The label of a focused field cuts through the focus ring as well as the frame, so the ring no longer runs above the label. The label turns blue on focus and red on a wrong value in every field, also in lists and in fields without a placeholder.
- The label of a disabled field no longer sits on a white patch: below the line it takes the gray of the field.

## [0.1.3] - 2026-11-14

### Changed

- Legal review: the default note of §19 invoices quotes §34a UStDV, "Für diese Leistung gilt die Steuerbefreiung für Kleinunternehmer (§ 19 Abs. 1 UStG)", since §19 is a tax exemption from 2025. The note of teaching names the service, "Steuerfreie Unterrichtsleistung gemäß § 4 Nr. 21 UStG". Zeile 13 of the report quotes the Anlage EÜR 2025 in full ("sowie Satz 2"). Issued invoices keep their text.

- "Tätigkeit" with two tax numbers: a customer, a template and a draft name the business, "Unterricht" or "Übrige Leistungen". It picks the tax number of the invoice and its Anlage EÜR, apart from the tax category, so teaching abroad ("nicht steuerbar") carries the teaching number and counts for the teaching Anlage. A template goes ahead of the customer, so one customer can have a teaching template and one for other work. Without a business, teaching only is "Unterricht". The issue freezes it.
- Teaching under § 4 Nr. 21 stands alone on its invoice, with one tax number or two; the issue names the problem of a mixed draft.

### Added

- `docs/verfahrensdokumentation.md`: the template of the GoBD process documentation for invoicing with the app.

## [0.1.2] - 2026-11-14

### Changed

- URLs and parameters are English: `/invoices`, `/customers`, `/reports`, `/account`, `/settings`, `/admin/users` and so on, `?year=`, `?q=`, `?part=`. The interface stays German, and so do the names of downloaded files.

- New look in the style of Preline UI, with blue accents and JetBrains Mono: white cards with thin lines and a soft shadow, labels on the top line of each field, muted and smaller, with a red star on required fields, a blue frame around the field in focus and a red one around a wrong field. Every button has a frame and an icon; destructive actions are red. Every select opens a styled list with a check mark where a mouse or trackpad is used; typed letters filter a list from nine entries on. Phones keep the picker of the system. Deleting a customer, draft or template, cancelling, issuing, renaming a passkey and setting a user's password happen in a dialog. The settings tabs underline the open tab; invoice statuses have colors; the Anlage EÜR names each line with a badge; the audit log marks each kind of entry with an icon; long customer names wrap instead of being cut off. The buttons of a form or dialog sit on one line at its right edge; a dialog grows wider instead of wrapping them.
- Several contact persons of one organization: each is a customer of its own, and every list and choice shows "Organisation · Ansprechpartner": customers, new invoice, invoices, overdue invoices on the start page, payments of the report and its CSV, the table of contents of the archive and the audit log. "Weiterer Ansprechpartner" on a customer opens a new customer with the same address, VAT ID, buyer reference and format.
- Fields, selects, checkboxes and buttons share one corner radius of 4px; cards keep 12px.
- Two tax numbers: "Steuernummer für Unterricht (§ 4 Nr. 21)" on the tab "Steuer" takes the second number when the Finanzamt gave teaching one of its own. An invoice of teaching only names it, every other invoice the main number; teaching and other lines then go on separate invoices. "Berichte" shows one Anlage EÜR per tax number with its number, and the CSV has a column "davon § 4 Nr. 21 UStG". The §19 threshold stays one, and so does the number sequence.
- A new password is typed twice, on "Mein Konto" and in the user administration; two different entries are refused.

- Profil: four tabs, Anschrift, Kontakt, Steuer and Bankverbindung, each with its own "Speichern". An incomplete profile saves; every empty field an invoice needs shows in red with "Für Rechnungen nötig", marked with a star, and a tab with such a field carries a red dot. A wrong value, such as an IBAN with a wrong checksum, shows its problem at the field and the tab is not saved. Issuing an invoice names what the profile still misses. E-mail and phone are needed for XRechnung only. Ansprechpartner and Kontoinhaber show the name until they are changed.

## [0.1.1] - 2026-11-14

### Added

- Automatische Sicherung: "Datensicherung" shows the last nightly backup and the last monthly restore test of the host, with what each reported, and warns when the backup is older than 36 hours or the restore test older than 35 days. The scripts on the host write table `backup_run`; the app only reads it.

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
