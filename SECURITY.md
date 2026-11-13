# Security policy

## Reporting a vulnerability

Report a vulnerability privately through GitHub:
https://github.com/grigoriev/im/security/advisories/new
(the **Security** tab, **Report a vulnerability**). Do not open a public issue for it.

We answer within a week. The fix goes into the next release, and its release notes name it.

## Supported versions

Only the latest image, `ghcr.io/grigoriev/im:latest`, gets fixes.

## Scope

The application code, the Dockerfile, the scripts and the workflows of this repository.

Vulnerabilities in upstream software (Spring Boot, Mustang, the KoSIT validator, PostgreSQL, the
base image and the frontend libraries) belong to the upstream project. Tell us as well if this
project is affected, so we can release a fix when the upstream fix is out.

## Deployment

The app is built for a home network. Run it behind a reverse proxy with TLS, and do not expose
it to the internet.
