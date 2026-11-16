# Eidelweiss

Android eID diagnostics and **regular Governikus self-disclosure** using the official, unmodified [AusweisApp Android SDK](https://www.ausweisapp.bund.de/sdk/android.html).

The app has three clearly separated features:

1. **Genuine normal self-disclosure:** the published `com.governikus:ausweisapp:2.6.2` AAR handles PACE, EAC (TA/CA), TLS/PAOS, and NFC. The official production `selfAuthentication.url` from AusweisApp's `resources/config.json.in` is used. Eidelweiss displays the signed certificate's service-provider disclosures, validity, and requested data categories and asks for explicit approval before sending `ACCEPT`. PIN/CAN/PUK are entered only when requested by the SDK.
2. **Passive NFC observation:** when no authentication is active, Android reports non-identifying ISO-DEP capabilities. No card APDU is transmitted by this feature.
3. **Offline synthetic EAC error matrix:** deterministic reference state machines for regression hypotheses. **These are not live chip tests**.

## Technical architecture

```text
Android NFC Reader Mode -> Tag -> AusweisAppSdkBridge.updateNfcTag()
                                         |
                     Official Governikus Android AAR 2.6.2
                                         |
                  Governikus production self-disclosure
                                         |
                               AUTH result -> URL
                                         |
               explicit action: open result in browser

OfflineMatrix (synthetic) ---> DiagnosticReport JSON
Passive NFC (no APDUs) ----> DiagnosticReport JSON
Normal SDK AUTH (redacted) -> DiagnosticReport JSON
```

The embedded SDK uses a bound Android service and the official AIDL interface. We do **not** copy or modify the AusweisApp EAC implementation. Unlike `eidolon`, Eidelweiss does not relay real-card APDUs to another host or emulate a card.

Source references:
- [Governikus Android SDK AIDL](https://www.ausweisapp.bund.de/sdk/android.html)
- [SDK RUN_AUTH / GET_CERTIFICATE / ACCEPT](https://www.ausweisapp.bund.de/sdk/commands.html)
- [AusweisApp selfAuthentication.url](https://github.com/Governikus/AusweisApp/blob/community/resources/config.json.in)
- [Eidolon Android NFC ideas](https://github.com/mtschirs/eidolon)

## Build

Requires Java 17, Gradle 8.13, Android SDK 36; an NFC-capable Android device supporting extended-length ISO-DEP communication (API 28+). Android Studio can import the project. The Gradle dependency downloads the official 2.6.2 AAR from Maven Central; **no local AusweisApp recompilation is required**.

```sh
gradle testDebugUnitTest
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow builds and tests the app and uploads a debug APK artifact. An APK build validates compilation only; the real eID flow additionally requires an NFC device, enabled online-ID function, and a current Governikus service.

## Normal self-disclosure walkthrough

1. Open Eidelweiss and ensure **AusweisApp-SDK bereit** is displayed.
2. Tap **Selbstauskunft starten**. Keep the nPA at the NFC antenna when requested.
3. Eidelweiss fetches the signed provider certificate using `GET_CERTIFICATE` after `ACCESS_RIGHTS` and displays the provider, legal terms including address/email/supervisory authority, expiration, and all requested data categories.
4. Approve or cancel explicitly. Enter PIN/CAN/PUK only on SDK request; the app does not store, log, or export their values.
5. When the SDK returns its final `AUTH` message, tap **Selbstauskunft-Ergebnis im Browser öffnen**. The refresh URL is kept **in-memory only** and omitted from diagnostic reports.
6. Optionally share a JSON report. The exported AUTH result only includes start/end timestamps, `ok`/`error`, a sanitized failure identifier, whether the certificate/rights were shown, and whether an NFC tag was forwarded.

The production mode always sets `developerMode=false`; no configurable eID server, insecure TLS mode, or repeat-TA mechanism is offered. The SDK is the only component that communicates with the chip during live authentication.

## Limits

- A successful `AUTH` result means the official SDK reported a completed authentication. It does **not** establish success of a second TA or a DA1/DA2 identity-binding issue.
- The diagnostic reports contain synthetic-model results but **no** live TA fault injection or card APDU traces.
- The public Governikus endpoint is not an authorization to run disruptive protocol experiments. Any repeat-TA or malformed-EAC research should use a separately authorized lab server and test cards.
- The official SDK requires extended-length NFC support; unsupported devices cannot complete the live flow.
- The SDK runs as an embedded background service and is not equivalent to launching the installed standalone AusweisApp UI.
- The application is research software, not a certified eID client.

## Attribution and licensing

The AIDL contract included here is copied from Governikus/AusweisApp and is licensed under the [EUPL-1.2](https://eupl.eu/1.2/en/) (copyright Governikus Service GmbH). The unmodified `com.governikus:ausweisapp` AAR is fetched from Maven Central and retains its own licenses. No external cryptographic source files, private credentials, or certificates are committed.
