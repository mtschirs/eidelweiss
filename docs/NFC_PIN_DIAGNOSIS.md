# NFC- und PIN-Diagnose für den echten Test-PKI-Durchlauf

## Fehlerbild vom 16. November 2026

Der gezeigte JSON-Bericht enthält bei allen drei abgebrochenen Selbstauskünften `SDK_INSERT_CARD` und `NFC_TAG_DISPATCHED`, **aber kein `SDK_ENTER_PIN`, `SDK_ENTER_CAN` oder `SDK_PAUSE`**. Die Abschlüsse haben den Fehlergrund `User_Cancelled`, weil der Benutzer nach der Wartezeit selbst abgebrochen hat. Das beweist **keine** chipseitige EAC2-Ablehnung.

`NFC_TAG_DISPATCHED` steht ausschließlich für einen von der SDK-AIDL-Schnittstelle bestätigten `updateNfcTag()`-Aufruf. Der native SDK-Reader muss den Tag danach erst erfolgreich als ISO-DEP-Ausweis erkennen.

## Korrektur

Zuvor rief `MainActivity.onTagDiscovered()` zunächst `ui.post { ... }` auf und erst darin `sdkBridge.forwardTag(tag)`. Das kann die NFC-Übergabe zeitlich verzögern und entspricht nicht dem Governikus-SDK-Beispiel, das `updateNfcTag` **direkt** aus `NfcAdapter.ReaderCallback.onTagDiscovered` aufruft.

Jetzt wird `updateNfcTag` unmittelbar auf dem NFC-Callback-Thread ausgeführt. Der UI-Thread übernimmt anschließend nur Statusanzeige und Ereignisprotokoll. Die App verbindet sich nicht selbst mit `IsoDep` und überträgt keine eigenen APDUs.

Zusätzlich fragt sie nach einer vom SDK angenommenen Übergabe mit `GET_READER_LIST` den **tatsächlichen SDK-NFC-Readerstatus** ab; sie speichert nur vorab definierte, datensparsame Ereignisnamen. Nach acht Sekunden ohne PIN/CAN/PUK-Anforderung wird `SDK_NO_SECRET_PROMPT_AFTER_NFC` protokolliert und eine klare Diagnose angezeigt. Diese Marke ist **kein Beleg für eine Fehlfunktion des Chips**; sie bedeutet nur, dass die SDK-Abfrage bis zu diesem Zeitpunkt nicht einging.

## Auswertung des nächsten Berichts

| Ereignis | Aussage |
| --- | --- |
| `NFC_TAG_DETECTED` | Android hat ein NFC-Tag erkannt |
| `NFC_TAG_DISPATCHED` | SDK-AIDL hat den Tag angenommen; noch **keine** native Erkennung |
| `SDK_READER_CHECK_REQUESTED` | `GET_READER_LIST` wurde angefordert |
| `SDK_NFC_READER_MISSING` | Die SDK-Leserliste enthält keinen NFC-Leser |
| `SDK_NFC_READER_DETACHED` | SDK-NFC-Leser ist nicht angeschlossen |
| `SDK_NFC_READER_NO_CARD` | SDK hat den NFC-Leser, aber **keinen** Ausweis erkannt |
| `SDK_NFC_READER_UNKNOWN_CARD` | SDK hat ein unbekanntes NFC-Kartenobjekt |
| `SDK_NFC_READER_CARD_RECOGNIZED` | SDK meldet eine unterstützte eID-Karte |
| `SDK_ENTER_PIN` | SDK hat die PIN-Abfrage tatsächlich ausgelöst |
| `SDK_NO_SECRET_PROMPT_AFTER_NFC` | Nach 8 Sekunden keine PIN-, CAN- oder PUK-Anforderung |

Die Datenschutzmaßnahmen bleiben erhalten: kein NFC-UID, kein Zertifikatsvolltext, keine PIN/CAN/PUK, kein TC-Token/Session-Identifier, keine Roh-APDUs im Bericht.

## Testschritte

1. Aktualisierte Debug-APK installieren und die **Test-PKI** auswählen.
2. Reguläre Selbstauskunft starten, Zertifikat und Berechtigungen kontrollieren und ausdrücklich freigeben.
3. Physische Testkarte ruhig am NFC-Leser halten. **Nicht unmittelbar nach dem ersten NFC-Ton abbrechen** – das Ereignis beweist noch keine SDK-Kartenerkennung.
4. Auf `SDK_NFC_READER_CARD_RECOGNIZED` und danach `SDK_ENTER_PIN` achten. Falls die PIN ausbleibt, den Status in der App und anschließend den JSON-Bericht prüfen.
5. Den Bericht ausgeben. Er entscheidet, ob die Kette vor der nativen SDK-Kartenerkennung, nach Erkennung oder in einem späteren EAC-Status stockt.

Die Standard-GitHub-Actions-APK verwendet weiterhin `com.governikus:ausweisapp:2.6.2`. Die native EAC2-Fehlerinjektion ist nur nach Einbindung der instrumentierten Qt-AAR verfügbar; der SDK-Engine-Typ wird seit dieser Änderung im Bericht separat markiert.

**Zur zweiten Selbstauskunft:** Ein neuer Test-PKI-`RUN_AUTH` kann einen neuen Servervorgang mit frischer TA-Signatur starten. Für die entscheidende Aussage muss die AusweisApp-C++-Engine beim zweiten Vorgang jedoch die ursprünglich aufgebaute PACE/SM-Kartensitzung wiederverwenden statt sie zu bereinigen oder erneut PACE auszuführen. Der vorliegende NFC-Fix implementiert **keine** solche Wiederverwendung.
