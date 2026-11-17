# im

[![CI](https://github.com/grigoriev/im/actions/workflows/ci.yml/badge.svg)](https://github.com/grigoriev/im/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/grigoriev/im/badge)](https://scorecard.dev/viewer/?uri=github.com/grigoriev/im)
[![Java 25+](https://img.shields.io/badge/Java-25%2B-blue)](pom.xml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=grigoriev_im&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=grigoriev_im)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=grigoriev_im&metric=coverage)](https://sonarcloud.io/summary/new_code?id=grigoriev_im)

Invoice Manager: a small web app that creates German e-invoices (e-Rechnung) for a freelancer.

- ZUGFeRD (PDF/A-3 with embedded XML) and XRechnung, validated with the official KoSIT validator.
- Kleinunternehmer rules (§19 UStG) with tax-exempt teaching (§4 Nr. 21 UStG), and threshold tracking.
- Regular VAT at 19 % and 7 % for every other business, for example the sale of goods, with the VAT per rate.
- Customer templates, immutable issued invoices, sending by e-mail (HTML and text, with a preview) or a manual dispatch
  record.
- Cancellation by a Stornorechnung: a validated credit note with its own number that names the cancelled invoice.
- Payments and refunds by the day the money came in, which the income report and the threshold use.
- Payment reminders for open invoices: by e-mail with the open amount, or noted by hand, with their history.
- Issued PDF and XML stay in the database with their SHA-256, so one `pg_dump` backs up everything.
- A store of business documents: the Verfahrensdokumentation, tax assessments, letters, certificates and contracts.
  Every file stays, a new version replaces it in the list, and the yearly archive takes them along. The app writes
  the Verfahrensdokumentation from the profile.
- Full export and import of all data and documents as one ZIP, to move to a new host.
- The admin page shows the last nightly backup and restore test of the host, and warns when one is overdue.
- Login with passkeys (Touch ID, Face ID) and a password fallback.
- Business expenses with their receipts, summed by kind for the Anlage EÜR in WISO Steuer, with a CSV.
- Seals every PDF, XML and document with a key outside the database, checks them every night and shows what a change
  in the database altered.
- Runs as a container with PostgreSQL in a home network.

The project is in development; production use starts in 2027. See [docs/PLAN.md](docs/PLAN.md) for
the plan and [docs/mockup](docs/mockup) for the UI mockup.

## Run

The image is `ghcr.io/grigoriev/im`. It needs PostgreSQL. Each start of a new version runs two steps: first the
migration of the database, then the app:

```sh
docker run --rm \
  -e IM_DB_URL=jdbc:postgresql://db:5432/im -e IM_DB_USER=im_app \
  -e IM_DB_OWNER=im -e IM_DB_OWNER_PASSWORD=... -e IM_INTEGRITY_KEY=... \
  ghcr.io/grigoriev/im:latest migrate
docker run -p 8080:8080 \
  -e IM_DB_URL=jdbc:postgresql://db:5432/im -e IM_DB_USER=im_app -e IM_DB_PASSWORD=... -e IM_INTEGRITY_KEY=... \
  -e IM_ADMIN_USERNAME=admin -e IM_ADMIN_PASSWORD=... -e IM_REMEMBER_ME_KEY=... \
  -e IM_WEBAUTHN_RP_ID=rechnungen.example -e IM_WEBAUTHN_ALLOWED_ORIGINS=https://rechnungen.example \
  ghcr.io/grigoriev/im:latest
```

The app uses two database roles. The migration runs as the owner of the tables, `IM_DB_OWNER`, and ends. The app
connects as `IM_DB_USER`, which may only read and write rows: only the owner can switch off the triggers that keep
issued invoices and documents unchanged. The app never gets the owner's password, so who gets into the app cannot
change an issued invoice. It refuses to start as the owner or as a superuser, and on a schema older than its version.
In Docker Compose the app waits for the migration with `condition: service_completed_successfully`. Create the role
once, before the first start:

```sh
psql -U im -d im -c "CREATE ROLE im_app LOGIN PASSWORD '...'"
```

A restore of a `pg_dump` into a new server needs the role too, since the dump grants it its rights.

`IM_INTEGRITY_KEY` seals each PDF, XML, snapshot and document with an HMAC-SHA256. Set a random value of at least 32
characters and keep a copy apart from the backups: an import checks the seals with the same key. A change made in the
database with the owner's password then shows on the invoice, on the overview and in the audit log. The app keeps a
changed file back from download, e-mail and the yearly archive.

`IM_ADMIN_USERNAME` and `IM_ADMIN_PASSWORD` create the first admin at startup while no admin exists. The
admin creates the other users on the page "Benutzer" and deletes them there. Once a second admin exists, the first
one can go: it does not come back at the next start. `IM_REMEMBER_ME_KEY` signs the "Angemeldet bleiben"
cookie: set a random value of at least 32 characters. The app does not start without it.

Passkeys need HTTPS, or `http://localhost`. `IM_WEBAUTHN_RP_ID` is the host name of the app, and
`IM_WEBAUTHN_ALLOWED_ORIGINS` lists its origins, comma separated. A passkey works only on the host it
was created for. Each user adds passkeys on the page "Mein Konto". The admin can remove them.

Sending invoices by e-mail needs an SMTP server: `IM_SMTP_HOST`, `IM_SMTP_PORT` (587), `IM_SMTP_USERNAME`,
`IM_SMTP_PASSWORD`, `IM_SMTP_STARTTLS` (`true`), the sender `IM_MAIL_FROM` and an optional blind copy
`IM_MAIL_BCC`. Without `IM_SMTP_HOST` and `IM_MAIL_FROM` the app sends nothing, and the user records each
dispatch by hand. Both roles set the default e-mail text and the text of a payment reminder on "E-Mail".

The app takes uploads up to 4 GB for the backup import. A reverse proxy should allow that size for
`/admin/backup/import` only and about 32 MB for the rest: the largest document has 25 MB.

The app installs on a phone as a web app. On an iPhone, open it in Safari and choose "Zum Home-Bildschirm". It
then opens without the browser bar, with its own icon. On a phone, "Ausgaben" offers the camera for the receipt.
Android and desktop Chrome also offer the shortcuts "Neue Rechnung" and "Neue Ausgabe" on the icon. The app keeps
no data offline: it needs the connection to the server.

Verify an image: `gh attestation verify oci://ghcr.io/grigoriev/im:<version> --owner grigoriev`.

## Build

See [CONTRIBUTING.md](CONTRIBUTING.md).

This software does not give tax advice. Check the invoice texts and the tax rules with a tax
professional.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Report vulnerabilities privately, see [SECURITY.md](SECURITY.md).

## Disclaimer

This software is provided "as is", without warranty of any kind, as the LICENSE states. Use it at
your own risk. Sergey Grigoriev is not liable for damage from its use, as far as the law allows. It
is published free of charge, outside of any commercial offering, with no obligation to support it.
Security reports are welcome, see SECURITY.md.

## License

MIT, see [LICENSE](LICENSE).
