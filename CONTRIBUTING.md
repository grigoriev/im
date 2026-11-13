# Contributing

Issues and pull requests are welcome.

## Build and test

Java 25 and Docker are required. Maven installs Node for the CSS build by itself.

```sh
mvn verify                                   # unit tests with the 100 % coverage gate, integration tests, PIT
mvn package -DskipTests && scripts/e2e.sh    # E2E and visual tests in Docker
scripts/e2e.sh --update                      # write new screenshot baselines
mvn test -Dtest=PdfVisualTest -Dvisual.update=true   # write new PDF baselines
```

CI runs the lint job, the build with SonarCloud, the E2E tests and the Claude Code review for every
pull request.

## Pull requests

1. Branch from the default branch as `type/description`, for example `fix/empty-title`.
2. Keep one change per pull request. New behavior comes with tests; a bug fix adds a test that
   fails without it.
3. Write commit messages as [Conventional Commits](https://www.conventionalcommits.org/) without a
   scope: `feat: ...`, `fix: ...`, `docs: ...`, `refactor: ...`, `test: ...`, `build: ...`,
   `ci: ...`, `chore: ...`.
4. Sign your commits. The default branch accepts verified signatures only.
5. Add an entry under `## [Unreleased]` in `CHANGELOG.md`, written for users: the release notes
   quote it. Update the README when behavior or configuration changes.

Pull requests are squash-merged once all required checks are green.

## Releases

A maintainer runs the Bump Version workflow. It moves the Unreleased entries into a versioned
section, tags the release, and the release workflow publishes the image with signed build
provenance.
