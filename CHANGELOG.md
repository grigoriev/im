# Changelog

All notable changes to this project are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Implementation plan (`docs/PLAN.md`) and clickable UI mockup (`docs/mockup`).
- Regular VAT at 19 % and 7 % next to the exempt categories, with net total, VAT per rate and gross total.
- Invoice lines are lump sums by default: the PDF shows text and amount, the quantity columns appear only when a line has a quantity.
- E-invoice chain: PDF/A-3B layout, ZUGFeRD (EN16931, XRECHNUNG) and XRechnung, validated with Mustang and KoSIT.
- Start page with light and dark theme, served with its own fonts and libraries.
- Container image, PostgreSQL with Flyway, and the release workflow to ghcr.io.
- Repository baseline: security policy, contribution guide, Claude Code review, Scorecard, Renovate.
