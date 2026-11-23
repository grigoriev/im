# im

[![CI](https://github.com/grigoriev/im/actions/workflows/ci.yml/badge.svg)](https://github.com/grigoriev/im/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/grigoriev/im/badge)](https://scorecard.dev/viewer/?uri=github.com/grigoriev/im)
[![Java 25+](https://img.shields.io/badge/Java-25%2B-blue)](pom.xml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=grigoriev_im&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=grigoriev_im)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=grigoriev_im&metric=coverage)](https://sonarcloud.io/summary/new_code?id=grigoriev_im)

Invoice Manager: a small web app that creates German e-invoices (e-Rechnung) for a freelancer.

- ZUGFeRD (PDF/A-3 with embedded XML) and XRechnung, validated before issue: ZUGFeRD with the Mustang validator and
  veraPDF, XRechnung with the official KoSIT validator.
- Kleinunternehmer rules (§19 UStG) with tax-exempt teaching (§4 Nr. 21 UStG), and threshold tracking.
- Regular VAT at 19 % and 7 % for every other business, for example the sale of goods, with the VAT per rate.
- Customer templates, immutable issued invoices, sending by e-mail (HTML and text, with a preview) or a manual dispatch
  record.
- Cancellation by a Stornorechnung: a validated credit note with its own number that names the canceled invoice.
- Payments and refunds by the day the money came in, which the income report and the threshold use.
- Payment reminders for open invoices: by e-mail with the open amount, or noted by hand, with their history.
- External invoices: an invoice made outside the app, in a form a customer demands or before the app, goes in with its
  original file (PDF, XLSX, DOCX or XML) and the facts typed in by hand. Its number must be the next one. A year
  before the app takes the numbers of its files in any order, with a reason for each gap, until it is closed.
- Issued PDF and XML stay in the database with their SHA-256, so one `pg_dump` backs up everything.
- A store of business documents: the Verfahrensdokumentation, tax assessments, letters, certificates and contracts.
  Every file stays, a new version replaces it in the list, and the yearly archive takes them along. The app writes
  the Verfahrensdokumentation from the profile.
- Full export and import of all data and documents as one ZIP, to move to a new host. The ZIP is not encrypted: it holds
  all customer data, IBANs and password hashes. Keep it on an encrypted medium only and delete it after use; the export
  page says so.
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
docker run -p 127.0.0.1:8080:8080 \
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

After an update, the line below the menu names the running version. The admin page "System" (`/admin/system`)
shows it with the time of the build, the schema of the database, PostgreSQL and Java, so you can check that the new
image and its migration arrived.

`IM_INTEGRITY_KEY` seals each PDF, XML, snapshot and document with an HMAC-SHA256. Set a random value of at least 32
characters and keep a copy apart from the backups: an import checks the seals with the same key. A change made in the
database with the owner's password then shows on the invoice, on the overview and in the audit log. The app keeps a
changed file back from download, e-mail and the yearly archive. The reports, payments and the Stornorechnung do not
compute with a changed snapshot either.

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

Run the app behind a reverse proxy with HTTPS and publish port 8080 to the proxy only, for example
`-p 127.0.0.1:8080:8080`. The app reads `X-Forwarded-For` and `X-Forwarded-Proto`
(`server.forward-headers-strategy=native`) and trusts them from every private address, so a client that reaches the
port directly from the local network could claim another address or HTTPS.

The app has no lockout after failed logins: a lockout would let anyone lock the owner out (owner decision
2026-11-22). Failed logins go into the audit log. Limit the rate of login attempts in the reverse proxy instead, for
the password and the passkey login. With nginx:

```nginx
# http block: count POST requests only; requests with an empty key are not limited
map $request_method $im_login_key {
    POST    $binary_remote_addr;
    default "";
}
limit_req_zone $im_login_key zone=im_login:10m rate=5r/m;

# server block of the app, next to "location /"
location ~ ^/login(/webauthn)?$ {
    limit_req zone=im_login burst=5 nodelay;
    limit_req_status 429;
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

`POST /login` is the password login, `POST /login/webauthn` the passkey login. The login page itself loads freely.

The app installs on a phone as a web app. On an iPhone, open it in Safari and choose "Zum Home-Bildschirm". It
then opens without the browser bar, with its own icon. On a phone, "Ausgaben" offers the camera for the receipt.
Android and desktop Chrome also offer the shortcuts "Neue Rechnung" and "Neue Ausgabe" on the icon. The app keeps
no data offline: it needs the connection to the server.

Verify an image: `gh attestation verify oci://ghcr.io/grigoriev/im:<version> --owner grigoriev`.

## Backup and restore

`pg_dump` of the database backs up everything: the documents, the original files and the receipts live in the
database. The admin page "Datensicherung" shows the last successful nightly backup and monthly restore test. The
scripts on the host write them into table `backup_run`, one row per kind; the app only reads it:

| Column | Type | Content |
|---|---|---|
| `kind` | `VARCHAR(16)`, primary key | `BACKUP` or `RESTORE_TEST` |
| `succeeded_at` | `TIMESTAMPTZ`, required | the time of the last success |
| `detail` | `VARCHAR(500)`, default empty | free text of the script, shown on the page |

```sql
INSERT INTO backup_run (kind, succeeded_at, detail) VALUES ('BACKUP', now(), '...')
ON CONFLICT (kind) DO UPDATE SET succeeded_at = EXCLUDED.succeeded_at, detail = EXCLUDED.detail;
```

The page warns when the backup is older than 36 hours or the restore test older than 35 days. A failed run writes
nothing; the script reports it by e-mail.

Restore a dump into a new server:

1. Start an empty PostgreSQL with the owner of the tables, for example `im`, and the database `im`.
2. Create the role of the app before the restore, since the dump grants it its rights:
   `psql -U im -d im -c "CREATE ROLE im_app LOGIN PASSWORD '...'"`.
3. Restore the dump as the owner, with `psql` or `pg_restore` depending on its format.
4. Set the same `IM_INTEGRITY_KEY` as before. With another key every seal fails, and the app keeps every document
   back from download, e-mail and archive.
5. Run the migration as the owner: `java -jar im.jar migrate`, or the image with the argument `migrate`. It brings
   a dump of an older version up to the schema of the image.
6. Start the app as `im_app`. It checks every seal at startup and names what a change altered.

The full export under "Datensicherung" is the other way to move to a new host: it goes only into an empty
installation with the same schema version ([ADR 0004](docs/adr/0004-import-only-into-an-empty-installation.md)).

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
