# 7. A REST API and an MCP server behind personal API tokens

- Status: accepted
- Date: 2026-11-24
- Recorded in: [docs/PLAN.md](../PLAN.md), section 5

## Context and problem

The users set up the app through its forms only. An assistant such as Claude Code could enter many customers or
settings at once, but it has no way in except to fill the forms like a browser, with a password and the CSRF token.

## Decision

- The app gets a REST API under `/api/v1` and an MCP server under `/mcp`. Both call the same services as the forms.
- Both accept a personal API token only, sent as `Authorization: Bearer im_...`. They accept no session cookie, so
  they need no CSRF protection.
- A user creates a token on "Mein Konto" after a recent login, as for a new passkey. The page shows the token once.
  The database keeps its SHA-256 hash only: the token carries 256 random bits, so a fast hash suffices.
- A token has a name, an expiry of at most one year and a scope: `READ`, or `WRITE` for reading and writing. It
  acts with the role of its user. A disabled or removed user ends its tokens, and so does a new password: a token
  made with a leaked password stops with it.
- The audit log records each change with the user and the note "API-Token" with the name of the token.
- The API and MCP leave out what cannot be undone: issuing, canceling and deleting invoices, payments, the import and
  the deletion of customers. These stay in the forms, where a person decides.
- The backup leaves the tokens out, as the remember-me logins. An import removes them.
- The API reads JSON only. It refuses an upload before the servlet stores it, and answers a refusal itself as problem
  details, without the error page and its session.
- The MCP server uses the stateless servlet transport of the MCP Java SDK and needs no Spring AI and no session. Its URL
  joins the filter chain of the API. Each tool runs on the thread of the request and calls the controller of the REST
  API, so both share the checks. The MCP server takes every call as a POST, so each tool checks the scope and the role
  itself.

## Consequences

- An assistant sets up the app in one go, and every change still passes the checks of the services.
- A stolen token works until it expires or its user deletes it. The app stays reachable from the home network and
  the VPN only, and each token use updates "zuletzt benutzt".
- New write operations in the API need a decision whether a person must make them in the form instead.
- What the API returns leaves the app through the assistant, to its provider. Customer data holds personal data, such
  as contact persons and e-mail addresses. The Verfahrensdokumentation and the record of processing name the
  assistant and its provider. A token for reading only suffices where nothing is written.
