# Nachweisprotokoll: EAC2-Fehlerfortsetzung, erneute TA und Diensteanbieterbindung

Stand: 2026-11-16. Der Zweck ist **Falsifizierbarkeit**, nicht das Erzeugen eines erfolgreichen Angriffsergebnisses. Die in Eidelweiss enthaltenen Offline-Modelle sind keine Evidenz für reale nPA-Chips.

## Untersuchungsgegenstand und Hypothesen

Drei voneinander unabhängige Eigenschaften sind zu belegen:

- **H-A: Client-Fortsetzung.** Eine untersuchte, eindeutig identifizierte AusweisApp-Version leitet nach einem genau benannten EAC2-Fehler noch TA-relevante APDUs weiter, statt die Transaktion zu beenden.
- **H-B: Akzeptanz einer zweiten TA.** Ein bestimmter physischer deutscher nPA akzeptiert eine **neue und kryptographisch gültige** TA, obwohl im Sinne von TR-03110-3 Anhang F dieselbe Secure-Messaging-Kartensitzung noch besteht.
- **H-C: Consent-/Identitätsbruch.** Ohne erneute, sichtbare Zustimmung wird ein Authentisierungsergebnis für einen anderen Diensteanbieter ausgestellt, als die zuvor angezeigte und freigegebene Terminalidentität.

**H-A allein ist kein Nachweis für H-B. H-B allein ist kein Nachweis für H-C.** Die Sicherheitswirkung ist erst mit vollständiger Dienst-zu-Dienst-Identitätsbindung beurteilbar.

Die TR-03127, Abschnitt 3.3.2, verbietet für deutsche Dokumente mehr als eine TA pro Kartensitzung. Die TR-03110-3, Anhang F.1/F.2, definiert die Sitzung durch Secure Messaging und erklärt, dass ein Wechsel der Schlüssel innerhalb derselben Sitzung keinen Sitzungswechsel darstellt. Eine HTTP-/PAOS-Servertransaktion ist **nicht** dieselbe Begriffsgröße wie die Kartensitzung.

## Randbedingungen eines gültigen Hardwareversuchs

1. **Genehmigtes Testsystem:** Ein ausdrücklich autorisierter Betreiber eines eID-Testservers und die für das Labor zugelassenen Terminalberechtigungen/Schlüssel. Für H-C müssen nachweislich zwei unterschiedliche, zugelassene Diensteanbieteridentitäten vorliegen. Den öffentlichen Selbstauskunftsdienst nicht als frei steuerbaren Testsignaturdienst behandeln.
2. **Echte Karte:** Physischer nPA mit dokumentierter Chipgeneration, Ausweisanwendung, Firmware-/Profilstand soweit verfügbar, NFC-Lesegerät und AusweisApp-Codeversion. Ein Simulator erfüllt diese Bedingung nicht.
   **PKI-Grenze:** Der reguläre Testdienst ist für Testausweise/Testzertifikate bestimmt. Ein produktiver nPA kann nur Zertifikatsketten akzeptieren, die auf einem tatsächlich im Chip verankerten CVCA-Vertrauenspunkt aufbauen. Ein eigenhändiger oder bloß im Android-Client vertrauter Test-CVCA erfüllt diese Voraussetzung nicht. Für einen Versuch auf einem produktiven nPA ist die Zusammenarbeit mit einem dafür autorisierten eID-Server-/Zertifikatsbetreiber erforderlich; ein Testausweis liefert nur Evidenz für dessen eigenes Profil.
3. **Vollständiger, unabhängiger Kartenkanal:** Ein autorisierter Testleser oder instrumentierter lokaler Kartenstack erfasst die Reihenfolge der relevanten ISO-7816-APDUs, SW1/SW2, Transport- und RF-Reset-Ereignisse und den Zeitpunkt von PACE- und CA-Secure-Messaging. Passwörter, APDU-Payloads, persönliche Daten und Sitzungsschlüssel gehören nicht in den freigegebenen Bericht.
4. **Serverbeobachtung:** Der Labor-eID-Server bestätigt die Gültigkeit der frisch erzeugten TA-Signatur und dokumentiert, ob sie zum konkreten Chip-Challenge-/PACE-Kontext passt. Die Signaturausstellung darf nicht allein aus einer HTTP-Statusmeldung gefolgert werden.
5. **Client- und UI-Beobachtung:** Versionierte AusweisApp mit differenziertem EAC2-/PAOS-Statusprotokoll, plus unabhängige Dokumentation der angezeigten Diensteanbieteridentität, der tatsächlich freigegebenen Rechte und des serverseitig bestätigten Resultats.
6. **Sitzungskontinuität nachweisen:** Zwischen den beiden TA-Versuchen dürfen keine Freigabe des Secure-Messaging-Kanals durch ungeschützte APDUs, kein unkontrollierter Reader-Reset und kein stillschweigender neuer PACE-Lauf stattgefunden haben. Ein bloß anliegender Ausweis oder gleicher NFC-Tag ist *kein* Nachweis.

### Vorsicht bei bestehenden Komponenten

Die Android-AIDL-Schnittstelle der offiziellen AusweisApp erlaubt RUN_AUTH und Statusnachrichten, gibt aber keine vollständige APDU-/TA-Sitzungshistorie heraus. Ein erfolgreiches zweites RUN_AUTH kann daher allein keine fortbestehende Kartensitzung nachweisen.

`eidolon` behandelt den Kartenstatus beim Wechsel von Card-Leases absichtlich defensiv: Ein Karten-Reset kann die ursprünglich interessierende Sitzung beenden. Ein solcher Test ist wertlos für den behaupteten gleichen Kartensitzungskontext. Ein anderer Reader-/Instrumentierungspfad müsste dies erst unabhängig belegen.

## Benötigte Mitwirkung eines Testbetreibers

Eine konkrete Anfrage an einen eID-Server-Betreiber beziehungsweise eine Prüfstelle sollte folgende Fähigkeiten abklären:

- Zwei getrennt nachweisbare Terminalidentitäten DA1/DA2 und ein klar definierter Laborberechtigungsumfang.
- Bereitstellung und unabhängige Prüfung einer **gültigen** TA-Antwort für den jeweils festgelegten Testzustand; keine Herausgabe privater Terminalschlüssel an Eidelweiss.
- Getrennt überprüfbare Server-, Client- und Chip-Messungen samt kryptographischen Zertifikatsreferenzen, ohne Speicherung personenbezogener Ausweisdaten in Freigabeprotokollen.
- Vorversuche mit PersoSim und physischen Testkarten, gefolgt von einem gesondert genehmigten Test mit produktivem nPA **nur falls** der beteiligte Betreiber eine passende zulässige Produktionszertifikatskette besitzt.
- Schriftlich bestätigte Freigabe der zulässigen Fehlerfälle und deren zeitliche/technische Grenzen.

Die offizielle AusweisApp-Testinfrastruktur nennt ausdrücklich eID-Service-Anbieter und eID-Server-Hersteller als Anlaufstellen für die Anbindung. Ohne diese Kooperation kann ein negativer Versuch an einer fehlenden gültigen TA-Signatur scheitern, ohne Aussagen über die chipseitige TA-Sperre zu erlauben.

## Minimale Versuchsmatrix

| Versuch | Erste TA | Zweiter Versuch | Zulässige Aussage |
| --- | --- | --- | --- |
| Baseline | Normale EAC mit DA1 | Kein zweiter Versuch | Client, Karte und Server interoperieren |
| Früher EAC2-Fehler | Noch nicht gestartet | Neue **gültige** TA | Prüft Fortsetzung nach frühem Fehler; die Einmal-TA-Bedingung wurde noch nicht ausgeschöpft |
| TA-Signaturfehler | Fehlgeschlagener TA-Beweis | Neue gültige TA | Chipgeneration und genauer Fehlerzustand entscheiden |
| CA-Fehler nach TA | Erste TA erfolgreich | Neue gültige TA | Testet die Einmal-TA-Regel trotz CA-Fehler |
| Erste TA abgeschlossen | Erste TA erfolgreich | Gleicher Terminalschlüssel | Muss gemäß allgemeinem EAC-Verbot zurückgewiesen werden |
| Erste TA abgeschlossen | Erste TA erfolgreich | Andere Terminalidentität | Muss beim nPA gemäß TR-03127 zurückgewiesen werden |
| Kontrollreset | TA abgeschlossen | Neue Sitzung mit neuem PACE und gültiger TA | Zeigt, dass die Signatur/Testinfrastruktur grundsätzlich funktioniert |
| UI-/Identitätsbindung | DA1 angezeigt und freigegeben | Ergebnis unter DA2 ohne neue Zustimmung | Für einen vollständigen H-C-Befund zwingend |

Die eigentliche Fehlererzeugung gehört ausschließlich in eine abgestimmte Laborumgebung. Dieses Repository implementiert **keinen** EAC2-Protokollmanipulator gegen den produktiven Governikus-Dienst.

## Akzeptanzkriterien

### Mögliche Bestätigung des gesamten Befunds

Alle folgenden Beobachtungen müssen unabhängig erhoben, positiv bestätigt und mit Originalartefakten referenziert werden:

- Kontrollierter EAC2-Fehler und dokumentierte Client-Fortsetzung (H-A).
- Gültiges, neues TA-Beweismaterial und chipseitige Akzeptanz einer zweiten TA (H-B).
- Ununterbrochene Kartensitzung im normativen Sinne, durch den Kartenkanal nachvollzogen.
- Eindeutig voneinander unterschiedliche DA1-/DA2-Zertifikatsidentitäten.
- Dokumentierte UI-Zustimmung nur für DA1.
- Unabhängig serverseitig bestätigte Authentisierung unter DA2 ohne neue Zustimmung (H-C).

Selbst dann ist die Bewertung auf die getesteten Chips, Codeversionen und Fehlerpunkte zu begrenzen; unabhängige kryptographische Sichtung ist erforderlich.

### Belastbare Widerlegung eines **konkreten** Versuchs

Ein negativer Test ist nur aussagekräftig, wenn ein zweiter **gültiger** TA-Beweis wirklich bis zur Karte gelangt, die gleiche Secure-Messaging-Sitzung nachgewiesen wurde, und die Chipantwort den TA-Versuch selbst ablehnt.

Eine abgebrochene HTTP-Anfrage, das Fehlen einer zweiten Signatur, ein NFC-Verbindungsabbruch oder die Ablehnung durch die AusweisApp sind **kein Nachweis**, dass der Chip die zweite TA selbst abgelehnt hat. Umgekehrt können sie H-A im konkret geprüften Clientpfad widerlegen.

Eine universelle Verneinung für alle nPA-Generationen oder alle Fehlerpositionen ist durch einzelne Negativtests nicht möglich.

## Reproduzierbarkeit und Beweissicherung

- Jede Ausführung bekommt eine eigene, nicht-personenbezogene `run_id`; Eidelweiss zeigt eine unabhängige SDK-Korrelationskennung.
- Ausführungsversionen: Android-Build-SHA, AusweisApp-/SDK-Version, Chip-/Readerprofil und Laborserver-Revision.
- Kontrollmessung ohne Fehler vor und nach dem Versuch.
- Je nach Messinstrument: unabhängige NFC-/APDU- und UI-/PAOS-/Serveraufzeichnungen mit synchronisierten Zeitstempeln.
- Unveränderte Rohdaten bleiben geschützt in der Laborumgebung; sensible personenbezogene Daten und Schlüssel niemals ins öffentliche GitHub-Repository einchecken.
- Jede freigegebene und gegebenenfalls redigierte Belegdatei wird mit SHA-256 referenziert.
- Die Repository-Prüfung `python3 tools/verify_lab_evidence.py <manifest.json>` prüft Struktur, Hashintegrität und logische Konsistenz. Sie **verifiziert nicht kryptographisch**, ob ein Server eine gültige TA-Signatur erstellt hat, und bewertet keine CC-Konformität.

## Relevante Spezifikationen

- [BSI TR-03127, Abschnitt 3.3.2](https://www.bsi.bund.de/SharedDocs/Downloads/DE/BSI/Publikationen/TechnischeRichtlinien/TR03127/BSI-TR-03127_1-40.pdf?__blob=publicationFile&v=3)
- [BSI TR-03110-2, Terminal Authentication](https://www.bsi.bund.de/SharedDocs/Downloads/EN/BSI/Publications/TechGuidelines/TR03110/BSI_TR-03110_Part-2-V2_2.pdf?__blob=publicationFile&v=1)
- [BSI TR-03110-3, Secure Messaging (Anhang F)](https://www.bsi.bund.de/SharedDocs/Downloads/EN/BSI/Publications/TechGuidelines/TR03110/BSI_TR-03110_Part-3-V2_2.pdf?__blob=publicationFile&v=1)
- [AusweisApp-SDK: Android AIDL](https://www.ausweisapp.bund.de/sdk/android.html)
- [AusweisApp-SDK: Nachrichten](https://www.ausweisapp.bund.de/sdk/messages.html)
