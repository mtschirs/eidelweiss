# AusweisApp-Codepfad nach EAC2-Fehler (statische Analyse)

Referenz: Governikus/AusweisApp Commit `c5ba637abc249d8a4b2b677789d6deae27f4e1ee`. Diese Analyse ist auf diesen Quellcode-Stand beschränkt. Sie enthält **keine** aktiven Tests gegen den öffentlichen Governikus-Dienst und keine Aussage über das Verhalten eines echten Chips.

## H-A: Bedingte Weiterleitung von PAOS Transmit

1. In `StateProcessCertificatesFromEac2::run()` wird bei fehlender CVC-Kette ein Fehler gesetzt und `fireAbort` ausgelöst.
2. In `StateDidAuthenticateEac2` wird bei ungültiger Zertifikatskette beziehungsweise Kartenkommando-Fehler ebenfalls `fireAbort` ausgelöst.
3. `CompositeStateTrustedChannel.cpp` verbindet sowohl den normalen als auch den Abbruchpfad dieser Zustände mit `StateSendDidAuthenticateResponseEAC2`.
4. `StateGenericSendReceive::run()` kodiert den aktuellen Fehler in der PAOS-Antwort, soweit er noch nicht an den Server gemeldet wurde. Es sendet diese Antwort auch nach einem lokal gesetzten Fehlerstatus.
5. `StateSendDIDAuthenticateResponseEAC2` erwartet als nächsten Servernachrichtentyp `PaosType::TRANSMIT`.
6. In `StateGenericSendReceive::onReplyFinished()` wird beim Empfang des erwarteten Nachrichtentyps `fireContinue` ausgelöst. Ein lokaler Fehlerstatus ist in diesem Verzweigungspunkt keine sichtbare Sperre.
7. Der `fireContinue`-Übergang führt nach `StateTransmit`. `StateTransmit::run()` übergibt die vom Server gelieferten `InputApduInfos` an `CardConnection::callTransmitCommand`.

Damit ist eine **quellcodeseitig erreichbare Fortsetzung** nach EAC2-Fehler gegeben, **sofern** der Server tatsächlich mit `Transmit` antwortet und die Netzwerk-/TLS-Prüfung erfolgreich bleibt.

## Was diese statische Analyse nicht belegt

- Ob ein produktiver eID-Server nach einem EAC2-Fehler eine `Transmit`-Nachricht sendet.
- Ob der übergebene APDU-Strom kryptographisch gültig ist.
- Ob Secure Messaging und der Kartenstatus bis dahin fortbestehen.
- Ob eine zweite TA auf einem echten nPA akzeptiert wird.
- Ob der Nutzer bei DA1 zustimmt, während DA2 authentisiert wird.

Der beurteilte Programmfluss ist kein Nachweis einer erfolgreichen Authentisierung und auch keine pauschale Aussage über die aktuelle Hauptentwicklung nach dem gepinnten Commit.

## Quellstellen

- [StateProcessCertificatesFromEac2.cpp](https://github.com/Governikus/AusweisApp/blob/c5ba637abc249d8a4b2b677789d6deae27f4e1ee/src/workflows/base/states/StateProcessCertificatesFromEac2.cpp#L21-L53)
- [StateDidAuthenticateEac2.cpp](https://github.com/Governikus/AusweisApp/blob/c5ba637abc249d8a4b2b677789d6deae27f4e1ee/src/workflows/base/states/StateDidAuthenticateEac2.cpp#L21-L86)
- [CompositeStateTrustedChannel.cpp](https://github.com/Governikus/AusweisApp/blob/c5ba637abc249d8a4b2b677789d6deae27f4e1ee/src/workflows/base/states/CompositeStateTrustedChannel.cpp#L119-L134)
- [StateGenericSendReceive.cpp](https://github.com/Governikus/AusweisApp/blob/c5ba637abc249d8a4b2b677789d6deae27f4e1ee/src/workflows/base/states/StateGenericSendReceive.cpp#L241-L345)
- [StateGenericSendReceive.h](https://github.com/Governikus/AusweisApp/blob/c5ba637abc249d8a4b2b677789d6deae27f4e1ee/src/workflows/base/states/StateGenericSendReceive.h#L193-L219)
- [StateTransmit.cpp](https://github.com/Governikus/AusweisApp/blob/c5ba637abc249d8a4b2b677789d6deae27f4e1ee/src/workflows/base/states/StateTransmit.cpp#L18-L52)
