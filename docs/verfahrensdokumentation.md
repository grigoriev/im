# Verfahrensdokumentation Rechnungsstellung

Vorlage nach den GoBD (BMF-Schreiben zu den Grundsätzen ordnungsmäßiger Führung und Aufbewahrung von Büchern,
Aufzeichnungen und Unterlagen in elektronischer Form, Rz. 151 ff.). Sie beschreibt, wie Ausgangsrechnungen mit der
Anwendung "Invoice Manager" erstellt, gespeichert und aufbewahrt werden.

Die Felder in eckigen Klammern füllt die Unternehmerin aus. Die ausgefüllte Fassung liegt nicht in diesem öffentlichen
Repository, sondern bei den Geschäftsunterlagen. Jede Änderung des Verfahrens wird mit Datum unter "Änderungen"
festgehalten.

## 1. Allgemeine Beschreibung

| Angabe | Inhalt |
|---|---|
| Unternehmerin | [Name, Anschrift] |
| Tätigkeiten | Unterricht nach § 4 Nr. 21 UStG; übrige Leistungen als Kleinunternehmerin nach § 19 UStG |
| Steuernummern | [Steuernummer Unterricht], [Steuernummer übrige Leistungen] |
| Gewinnermittlung | Einnahmenüberschussrechnung nach § 4 Abs. 3 EStG, je Steuernummer eine Anlage EÜR |
| Gültig ab | [Datum der ersten Rechnung mit der Anwendung] |

Die Anwendung erstellt Ausgangsrechnungen als PDF/A-3 mit eingebetteter XML (ZUGFeRD) oder als reine XML
(XRechnung), erfasst Versand und Zahlungseingänge und erstellt die Auswertung für die Anlage EÜR. Eingangsrechnungen
und Betriebsausgaben verwaltet sie nicht; diese werden [Verfahren für Belege und Ausgaben] erfasst.

## 2. Anwenderdokumentation

1. Die Unternehmerin legt Kunden und Vorlagen an.
2. Ein Entwurf ist änderbar. Die Anwendung prüft ihn auf Pflichtangaben und nennt, was fehlt.
3. "Rechnung ausstellen" vergibt die nächste Nummer des Nummernkreises lückenlos und fortlaufend, erzeugt PDF und
   XML und prüft sie gegen EN 16931 und das gewählte Format (Mustang, veraPDF, KoSIT). Scheitert die Prüfung, bleibt
   der Entwurf bestehen und die Nummer wird nicht verbraucht.
4. Eine ausgestellte Rechnung lässt sich nicht mehr ändern. Eine Korrektur erfolgt durch eine Stornorechnung mit
   eigener Nummer, die auf die ursprüngliche Rechnung verweist.
5. Der Versand wird mit Tag, Weg und Empfänger vermerkt. Der E-Mail-Versand aus der Anwendung vermerkt sich selbst.
6. Zahlungseingänge und Erstattungen werden mit dem Tag des Zahlungseingangs erfasst. Sie sind die Grundlage der
   Einnahmen in der Anlage EÜR und der Umsatzgrenze nach § 19 UStG.
7. Am Jahresende lädt die Unternehmerin unter "Berichte" die Werte der Anlage EÜR, die Zahlungen als CSV und das
   Archiv aller Rechnungen des Jahres herunter.

## 3. Technische Systemdokumentation

| Bestandteil | Beschreibung |
|---|---|
| Anwendung | Invoice Manager, Version [Version], Quelltext öffentlich unter github.com/grigoriev/im |
| Betrieb | Docker-Container auf einem eigenen Server im Heimnetz, nur aus dem Heimnetz erreichbar |
| Datenbank | PostgreSQL; Rechnungen, PDF, XML und ein unveränderlicher Datenstand je Rechnung |
| Unveränderbarkeit | Datenbank-Trigger verweigern jede Änderung einer ausgestellten Rechnung, ihrer Dokumente, Versandvermerke und Zahlungen |
| Prüfsummen | SHA-256 je gespeichertem PDF und XML, sichtbar in der Rechnung und im Jahresarchiv |
| Protokoll | Anmeldungen, Ausstellung, Storno, Versand, Zahlungen und Änderungen der Einstellungen mit Zeit und Benutzer |
| Zugang | Benutzerkonten mit Passkey oder Passwort, Rollen Benutzer und Administrator |

## 4. Betriebsdokumentation

1. Datensicherung: jede Nacht ein vollständiger Datenbankauszug, verschlüsselt auf einen zweiten Server außer Haus
   übertragen (restic). Aufbewahrt werden 7 tägliche, 8 wöchentliche, 24 monatliche und 10 jährliche Stände.
2. Wiederherstellungstest: jeden Monat automatisch; die Anwendung zeigt den letzten erfolgreichen Lauf. Ein Fehler
   meldet sich per E-Mail.
3. Aufbewahrung: Rechnungen 8 Jahre (§ 14b UStG, § 147 Abs. 3 AO), gerechnet ab dem Ende des Kalenderjahres. Die
   Jahresarchive mit PDF, XML und Inhaltsverzeichnis werden zusätzlich [Ablageort] abgelegt.
4. Datenzugriff der Finanzverwaltung: PDF und XML liegen im Originalformat vor; die Zahlungen als CSV, das
   Protokoll und die Prüfsummen sind maschinell auswertbar.
5. Änderungen der Anwendung werden als Versionen mit Änderungsprotokoll (CHANGELOG) veröffentlicht. Eine neue
   Version ändert keine ausgestellte Rechnung.

## Änderungen

| Datum | Änderung |
|---|---|
| [Datum] | Erstfassung |
