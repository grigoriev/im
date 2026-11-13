# im

[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/grigoriev/im/badge)](https://scorecard.dev/viewer/?uri=github.com/grigoriev/im)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=grigoriev_im&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=grigoriev_im)

Invoice Manager: a small web app that creates German e-invoices (e-Rechnung) for a freelancer.

- ZUGFeRD (PDF/A-3 with embedded XML) and XRechnung, validated with the official KoSIT validator.
- Kleinunternehmer rules (§19 UStG) with tax-exempt teaching (§4 Nr. 21 UStG), and threshold tracking.
- Customer templates, immutable issued invoices, sending by e-mail or a manual dispatch record.
- Login with passkeys (Touch ID, Face ID) and a password fallback.
- Runs as a container with PostgreSQL in a home network.

The project is in the planning stage. See [docs/PLAN.md](docs/PLAN.md) for the plan and
[docs/mockup](docs/mockup) for the UI mockup.

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
