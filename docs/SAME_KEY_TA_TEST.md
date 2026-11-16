# Einzeltest: dieselbe TA nach definiertem EAC2-Fehler

**Testziel:** Ein physischer Testausweis der Governikus-Test-PKI soll auf die Wiederholung einer Terminal Authentication mit demselben öffentlichen Terminalschlüssel innerhalb **derselben Secure-Messaging-Kartensitzung** geprüft werden.

**Einziger Fehlerpunkt:** `EAC2_RESULT_ERROR_AFTER_ACCEPTED_FIRST_TA`. Das bezeichnet eine bei der eID-Serverseite als Fehler gemeldete EAC2-Antwort, obwohl die **erste TA vom Chip bereits erfolgreich akzeptiert** wurde. Diese Reihenfolge muss durch unabhängige Laboraufzeichnungen bewiesen werden. Ein Fehler wie `Process_Certificates_From_Eac2_Cvc_Chain_Missing` erfüllt sie nicht: Hier kommt es gar nicht zu einer erfolgreichen ersten TA.

## Gültigkeitsbedingungen

1. Physischer Testausweis; passende, autorisierte Test-PKI; reguläre Governikus-Selbstauskunft als Kontrollmessung (in Eidelweiss bereits verfügbar).
2. Der **erste** signierte Terminal-Authentisierungsbeweis wurde vom Chip erfolgreich akzeptiert. Dies ist über den authentisierten Kartenstatus, nicht nur das SDK-`AUTH`-Ergebnis, zu prüfen.
3. Erst **danach** wird der definierte EAC2-Antwortfehler gegenüber einem **für den Test autorisierten** eID-Server festgestellt.
4. Derselbe nachweislich gültige Secure-Messaging-Kartenkontext bleibt erhalten: kein Karten-/RF-Reset, keine neue PACE-Ausführung. Eine zweite HTTP-/PAOS-Transaktion allein beweist dies nicht.
5. Der Laborserver gibt eine **neue, gültige** TA-Signatur für die aktuellen Sitzungsparameter aus. Dies ist **keine Wiederverwendung derselben alten TA-Signatur**.
6. Die Fingerabdrücke des **normalisierten öffentlichen Terminalschlüssels** aus beiden CVC-Zertifikaten sind identisch (`SHA-256`, Punkt- und Domainparameter-Kanonisierung durch das Labor). Ein gleicher Zertifikatsname allein genügt nicht.
7. Der Kartenstatus des zweiten `EXTERNAL AUTHENTICATE` wird vom autorisierten Reader-/Serverstack einschließlich geschützter innerer Antwort (DO'99/MAC bei Secure Messaging) verifiziert. Äußeres `9000` allein genügt nicht.

Diese Voraussetzungen sind nicht durch zwei unabhängige `RUN_AUTH`-Aufrufe mit dem unveränderten AusweisApp-AAR herstellbar. Das SDK bietet keine öffentliche Schnittstelle zur gezielten Injektion nach erster TA und keine bestätigte Möglichkeit, die Kartensitzung für einen zweiten Servervorgang aufrechtzuerhalten. Hierzu ist ein separates, ausdrücklich autorisiertes Laborinstrument erforderlich. Der öffentliche Governikus-Testdienst kann als reguläre Baseline verwendet werden; seine Bereitstellung bedeutet nicht automatisch, dass EAC2-Fehlermanipulationen oder beliebige TA-Signaturen autorisiert sind.

## Android-Testablauf

1. In Eidelweiss `Test-PKI (Testausweis)` wählen und eine **reguläre** Selbstauskunft als Baseline durchführen.
2. Der eigentliche Einzelfall wird unabhängig mit einem instrumentierten und genehmigten Laboraufbau durchgeführt; dort die oben genannten sieben Messpunkte erfassen.
3. Eine **redigierte Evidenzdatei** im Schema [same_key_ta.template.json](same_key_ta.template.json) erstellen, deren Angaben durch die Originalmessungen belegbar sind. Die Beispieldatei ist absichtlich **nicht** beweiskräftig.
4. In Eidelweiss **„Gleicher Terminalschlüssel: Evidenzdatei prüfen“** wählen und die JSON-Datei importieren.
5. Die App prüft die Gültigkeitsbedingungen und exportiert die Klassifikation im Diagnosebericht. Ein gemeldeter Erfolg ist **keine automatische kryptographische Attestierung**. Zusätzlich sind die redigierten Belege unabhängig zu prüfen.

## Ergebnislogik

- Erste TA nicht vom Chip akzeptiert: `FIRST_TA_NOT_COMPLETED` – es gibt noch keinen erfolgreichen ersten TA-Vorgang, der wiederholt worden wäre.
- Identischer PK, gleiche Sitzung, frische gültige zweite Signatur und authentisierte Chipablehnung: `REPORTED_SECOND_TA_REJECTED_TESTED_CASE_ONLY`.
- Dieselben Voraussetzungen mit authentisierter Chipakzeptanz: `REPORTED_SECOND_TA_ACCEPTED_REQUIRES_REVIEW`.
- Fehlende echte Messwerte, fremde Schlüssel oder andere Fehlerpositionen: `INCONCLUSIVE` beziehungsweise `INVALID_EVIDENCE`.

Der Einzelfall ist nicht mit einem vollständigen Angriff auf die Bindung von DA1/DA2 gleichzusetzen. Ein negatives Ergebnis gilt nur für die getestete Testkarte, Firmware und Fehlerposition.
