# 6. English technical names and no fully qualified class names

- Status: accepted
- Date: 2026-11-14
- Recorded in: [CLAUDE.md](../../CLAUDE.md), section "Code Style"

## Context and problem

The users read German only, but the code, the URLs and the database mixed German and English names. Fully qualified
class names inside the code made lines long and hid the dependencies of a class.

## Decision

- Technical names are English: URLs, request and form parameters, element IDs, CSS classes, code, database and JSON.
- What the user reads is German: texts, labels, messages and the names of downloaded files.
- German legal terms without an English equivalent stay as terms, for example `KLEINUNTERNEHMER`.
- Code imports a class instead of naming it fully qualified.

## Consequences

- One language per layer: a reviewer tells a technical name from a user text at a glance.
- The imports list every dependency of a class at its top.
