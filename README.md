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
- Customer templates, immutable issued invoices, sending by e-mail or a manual dispatch record.
- Payments and refunds by the day the money came in, which the income report and the threshold use.
- Issued PDF and XML stay in the database with their SHA-256, so one `pg_dump` backs up everything.
- Login with passkeys (Touch ID, Face ID) and a password fallback.
- Runs as a container with PostgreSQL in a home network.

The project is in development; production use starts in 2027. See [docs/PLAN.md](docs/PLAN.md) for
the plan and [docs/mockup](docs/mockup) for the UI mockup.

## Run

The image is `ghcr.io/grigoriev/im`. It needs PostgreSQL:

```sh
docker run -p 8080:8080 \
  -e IM_DB_URL=jdbc:postgresql://db:5432/im -e IM_DB_USER=im -e IM_DB_PASSWORD=... \
  -e IM_ADMIN_USERNAME=admin -e IM_ADMIN_PASSWORD=... -e IM_REMEMBER_ME_KEY=... \
  -e IM_WEBAUTHN_RP_ID=rechnungen.example -e IM_WEBAUTHN_ALLOWED_ORIGINS=https://rechnungen.example \
  ghcr.io/grigoriev/im:latest
```

`IM_ADMIN_USERNAME` and `IM_ADMIN_PASSWORD` create the first admin at startup while no admin exists. The
admin creates the other users on the page "Benutzer". `IM_REMEMBER_ME_KEY` signs the "Angemeldet bleiben"
cookie: set a long random value.

Passkeys need HTTPS, or `http://localhost`. `IM_WEBAUTHN_RP_ID` is the host name of the app, and
`IM_WEBAUTHN_ALLOWED_ORIGINS` lists its origins, comma separated. A passkey works only on the host it
was created for. Each user adds passkeys on the page "Mein Konto". The admin can remove them.

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
