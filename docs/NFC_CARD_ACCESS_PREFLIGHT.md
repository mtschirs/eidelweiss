# Unabhängiger NFC-Karten-Lesetest (ohne AusweisApp-SDK)

## Motivation

Im SDK-Bericht vom 16.11.2026 zeigen alle Durchläufe `NFC_TAG_DISPATCHED`,
aber kein `SDK_NFC_READER_CARD_RECOGNIZED`. Stattdessen erscheint wiederholt
`SDK_NFC_READER_NO_CARD`, kurzzeitig `SDK_NFC_READER_UNKNOWN_CARD`; die
PIN-Abfrage wird daher nicht erreicht. Der SDK-AIDL-Aufruf quittiert nur die
Übergabe eines Android-`Tag`, **nicht** eine erfolgreich aufgebaute
ISO-DEP-/Ausweisverbindung.

Der folgende Test trennt den **NFC-Transport** von der Kartenidentifikation
durch den Governikus-SDK-Prozess.

## Einfache Prüffolge

1. Sämtliche laufenden Selbstauskunftsvorgänge beenden.
2. Testausweis vollständig aus dem NFC-Feld entfernen.
3. In Eidelweiss auf **NFC-Lesetest: öffentliches EF.CardAccess** tippen.
4. Den Testausweis auflegen und ruhig liegen lassen, bis Eidelweiss das
   Resultat zeigt.
5. Das Resultat steht im JSON-Bericht unter `nfc_observations`; es enthält
   nur technische Rückgabekategorien, keine Antwortbytes.

Der Test läuft **nur außerhalb der Authentisierung**. Er kann nicht mit dem
SDK-NFC-Reader gleichzeitig dieselbe Karte benutzen. Eidelweiss verwendet
`IsoDep.connect()`, sendet genau zwei ungeschützte ISO-7816-APDUs und
schließt die Verbindung wieder:

```
SELECT EF.CardAccess: 00 A4 02 0C 02 01 1C
READ first octet:   00 B0 00 00 01
```

EF.CardAccess ist nach BSI TR-03110-3, Anlage A, öffentlich lesbar.
Es werden **keine personenbezogenen Ausweisdaten** gelesen oder exportiert,
nur das erste öffentliche ASN.1-Tag `31` geprüft. Der SW1/SW2-Status wird
in den Bericht übernommen; keine PIN/CAN/PUK, NFC-UIDs, APDUs, Schlüssel
oder PACE/TA-Informationen.

## Befundinterpretation

- `CARD_ACCESS_OK`: Android-NFC kann den Chip über ISO-DEP verbinden,
  EF.CardAccess selektieren und ein Byte lesen. Scheitert nur der SDK-Pfad,
  liegt der Fehler **nicht an einer grundsätzlich fehlenden NFC-APDU-Verbindung**.
  Das beweist noch keine stabile Verbindung über die gesamte EAC.
- `CONNECTION_LOST` oder `IO_ERROR`: Die Verbindung oder der
  Karten-Transport scheitert bereits beim sehr kurzen öffentlichen Lesezugriff.
  Telefonposition, Hülle, magnetische/metallische Unterlage und
  störende parallele NFC-Aktivitäten prüfen.
- `CARD_ACCESS_SELECT_REJECTED` / `CARD_ACCESS_READ_REJECTED`:
  Karte ist technisch erreichbar, aber der öffentliche eID-Dateizugriff
  wurde zurückgewiesen. Testkartentyp und ISO-7816-Status separat prüfen.
- `NON_ISODEP`: Falscher Tagtyp, kein kompatibler Ausweis.

Falls der Lesetest erfolgreich ist, danach **einen normalen**
Test-PKI-Selbstauskunftsvorgang starten. Dabei einen neuen NFC-/PACE-
Sitzungskontext verwenden; die Probe ist **keine** Wiederverwendung von
Secure Messaging.

## Normative Quellen

- [BSI TR-03110-3, EF.CardAccess File ID 011C und Lesezugriff ALWAYS](https://www.bsi.bund.de/SharedDocs/Downloads/EN/BSI/Publications/TechGuidelines/TR03110/BSI_TR-03110_Part-3_V2-1.pdf?__blob=publicationFile&v=1)
- [ICAO RF and Protocol Testing, ISO7816_Q_1: SELECT EF.CardAccess / READ BINARY](https://www.icao.int/sites/default/files/TRIP/Publications/ICAO-TR-RF-and-Protocol-Testing-Part-3.pdf)
- [Governikus SDK READER: card={} vs. card=null](https://www.ausweisapp.bund.de/sdk/messages.html)
