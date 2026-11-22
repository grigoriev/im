# Verfahrensdokumentation Rechnungsstellung

The app writes the Verfahrensdokumentation itself: "Dokumente", then "Verfahrensdokumentation", "Erstellen".

1. Fill in the profile: name, address and tax numbers.
2. Enter the four values of the form and download the preview.
3. Save it as a version, print it, sign it and keep the scan as the next version.

The text lives in `src/main/resources/templates/pdf/procedure.html`. Change it together with `docs/PLAN.md`, in the
same pull request (see the checklist in `CONTRIBUTING.md`), and update the baseline:
`mvn test -Dtest=PdfVisualTest -Dvisual.update=true`.

The table "Änderungen" lists the versions saved in the app, each with the change typed into the form. After an
update of the text, save a new version with a note of what changed, for example "Externe Rechnungen, Nummern vor der
Anwendung, Zugang über VPN".
