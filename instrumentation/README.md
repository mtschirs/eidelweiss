# Native EAC2-Laborinstrumentierung

Diese Variante instrumentiert **wirklich die C++-AusweisApp-Engine**. Anders als die bisherige Offline-Simulation setzt sie einen Fehler erst, nachdem der Testausweis die erste TA tatsächlich erfolgreich beantwortet hat.

**Was implementiert ist:** Einmalige lokale EAC2-Fehlerinjektion unmittelbar nach der ersten erfolgreichen `EXTERNAL AUTHENTICATE` (TA), vor Chip Authentication; Statusmarkierungen für TA, EAC2-Antwort, PAOS-Nachricht nach EAC2, an den Chip weitergeleitete `Transmit`-Batches, PACE-Neustart und Secure-Messaging-Abbau.

**Was nicht implementiert ist:** Ein zweiter gültiger TA-Beweis oder die Kopplung einer neuen Governikus-Test-PKI-Servertransaktion an denselben bestehenden Kartenkontext. Das ist eine separate serverseitige Protokollfähigkeit und kann **nicht** aus einem normalen zweiten `RUN_AUTH` oder dem AAR-Instrumentierungsflag folgen. Ohne passende zweite serverseitige Signatur **liefert dieser Aufbau noch keinen Nachweis für die TA-Wiederholungssperre**.

## Quellen und Versionsbindung

Die Instrumentierung verwendet exakt neun C++-Quelldateien aus `mtschirs/eidolon/AusweisApp`, deren ursprüngliche Git-Blob-SHAs in `instrumentation/base-manifest.json` hinterlegt sind. Die veränderten Dateien stehen in `instrumentation/ausweisapp-overrides`. Die Implementierung verändert die Upstream-Quellen in `eidolon` **nicht** automatisch oder remote.

Vorbereitung im eigenen Clone:

```sh
git clone --depth 1 https://github.com/mtschirs/eidelweiss.git
git clone --depth 1 https://github.com/mtschirs/eidolon.git
python3 eidelweiss/tools/prepare_eac2_native_sources.py eidolon/AusweisApp
```

Das Skript verweigert Änderungen, wenn die Originaldateien nicht zur gespeicherten Revision passen. Bei Versionabweichung die Quellen erneut prüfen, **keine** Validierung umgehen.

## Instrumentiertes Android-SDK erstellen

Ein Android-Qt-6-Build mit der für die vendorte AusweisApp benötigten Android SDK/NDK- und Qt-Umgebung ist erforderlich. Die projektinternen CMake-Presets enthalten `ci-android-aar-review` (`INTEGRATED_SDK=ON`). In diesem lokalen **Labor-Build** die Zusatzdefinition aktivieren:

```sh
cd eidolon/AusweisApp
cmake --preset ci-android-aar-review \
  -DCMAKE_CXX_FLAGS=-DEIDELWEISS_EAC2_LAB \
  -DCMAKE_PREFIX_PATH="<Qt Android toolchain / project dependencies>"
cmake --build --preset ci-android-aar-review
```

Achtung: Dieser Beispielaufruf ist kein universelles Buildrezept. Die Presets verweisen auf CI-spezifische Qt-/NDK-Abhängigkeiten und müssen an die lokale Qt-Toolchain angepasst werden. **Das native AAR wurde hier nicht erfolgreich für Android kompiliert**; die GitHub-CI baut gegen das **offizielle** SDK, solange kein lokaler AAR-Pfad angegeben wird.

Sobald der Qt-Build eine `.aar` erzeugt, Eidelweiss **explizit** gegen diese AAR bauen:

```sh
cd eidelweiss
gradle assembleDebug -PeidelweissLabAar=/absolute/path/to/AusweisApp-instrumented.aar
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ohne `eidelweissLabAar` verwendet Eidelweiss weiterhin `com.governikus:ausweisapp:2.6.2`, und die native Fehlerfunktion ist deaktiviert. In der Testoberfläche erscheint die Schaltfläche **„Echte TA, danach EAC2-Fehler (einmal)“** nur als nutzbare Aktion, wenn ein lokaler AAR-Pfad beim Build übergeben wurde.

## Testlauf mit physischem Testausweis

1. Normale Test-PKI-Selbstauskunft als Kontrollmessung durchführen.
2. Dieselbe Testkarte erneut anhalten und explizit **„Echte TA, danach EAC2-Fehler (einmal)“** bestätigen.
3. Der Client legt ein **einmaliges Flag** im privaten Android-App-Speicher an. Der separate Qt-SDK-Serviceprozess liest und löscht dieses Flag ausschließlich dann, wenn die `tcTokenURL` exakt auf den offiziellen Test-PKI-Selbstauskunftsdienst zeigt.
4. Die Native-Engine führt CVC-Prüfung, MSE Set AT und `EXTERNAL AUTHENTICATE` tatsächlich am Testausweis durch. Nur bei erfolgreich abgeschlossener erster TA bricht sie mit einem lokal eingesetzten EAC2-Fehler **vor CA** ab.
5. Der normale AusweisApp-Fehlerpfad versucht, das Fehlerresultat via PAOS an den eID-Server zu melden. Antwortet dieser mit `Transmit`, wird dessen Weiterleitung und das Ergebnis protokolliert; andernfalls der reguläre Abbruch.
6. Aufzeichnung des **separaten SDK-Serviceprozesses** via Android `logcat`, gefiltert nach `EIDELWEISS_EAC2_LAB` (keine Roh-APDUs, Zertifikate, Schlüssel oder PINs). Zusätzlich den JSON-SDK-Ereignisbericht exportieren.

Die Logzeile `phase=ta_first result=accepted_by_chip` entsteht unmittelbar **nach** einem erfolgreichen nativen TA-Aufruf, nicht aus der Android-SDK-`AUTH`-Nachricht.

Mit `phase=secure_messaging_stop` und `phase=destroy_pace_requested` lässt sich ein möglicher Sitzungsabbruch erkennen. **Ihre Abwesenheit beweist allerdings keine durchgängige RF-/Karten-Sitzung.** Dafür ist zusätzlich eine unabhängige Reader-Aufzeichnung erforderlich.

### Native Ablaufspur lokal auswerten

```sh
adb logcat -d -v epoch | grep EIDELWEISS_EAC2_LAB > eac2-native-markers.txt
python3 tools/summarize_native_eac2_log.py eac2-native-markers.txt > eac2-native-summary.json
```

Die Auswertung protokolliert den echten **ersten** TA-Erfolg, den injizierten Fehler und gegebenenfalls eine vom Testserver gesendete `Transmit`-Fortsetzung. Sie behauptet **keinen** gültigen zweiten TA-Beweis. Die Originalaufzeichnung gehört in eine zugangsgeschützte Laborablage; der JSON-Report führt nur redigierte Ereignisnamen.

## Scharfe Grenzen für die Ergebnisbewertung

- Dieser reale Eingriff zeigt, ob die **erste TA trotz anschließendem EAC2-Fehler** bereits erfolgreich war und ob der Testdienst danach ggf. weiter `Transmit` sendet.
- **Er erzeugt keine zweite gültige TA-Signatur.** Das Test-PKI-Selbstauskunftssystem muss hierfür einen unterstützten und genehmigten Serverablauf bereitstellen.
- Ein zweiter regulärer SDK-`RUN_AUTH` setzt die Kartensitzung grundsätzlich nicht nachweislich fort.
- Ein `9000` als ungeschützter APDU-Außenstatus genügt nicht, um eine zweite TA als akzeptiert einzuordnen.
- Der Build ist nur für den eigenen physischen **Testausweis** und die offizielle **Test-PKI** bestimmt. Der Produktionsdienst wird nicht als Fehlerorakel verwendet.
- Die Änderungen stammen aus einer EUPL-lizenzierten AusweisApp-Codebasis. Vor Weitergabe den Lizenztext und die ursprünglichen Copyright-Hinweise beibehalten.
