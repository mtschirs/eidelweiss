# Eidelweiss

Android NFC and offline EAC state-machine diagnostics for responsible German eID security research.

This repository contains a **non-invasive** research companion app: it observes Android ISO-DEP card discovery without transmitting APDUs, runs reproducible *synthetic* EAC fault cases offline, and exports a redacted JSON report. It does **not** implement live EAC fault injection, relay real-card APDUs, reuse authenticated sessions across services, or automate the public Governikus self-disclosure endpoint.

The design is inspired by the Android ISO-DEP activation diagnostics in the private [eidolon](https://github.com/mtschirs/eidolon) project, but deliberately does not copy its WebSocket relay or card-emulation components. The upstream [AusweisApp](https://github.com/Governikus/AusweisApp) remains the reference implementation for normal production authentication.

## What this establishes

- Whether an Android device detects a physical card as ISO-DEP and what non-identifying transport parameters it exposes.
- Whether a *synthetic reference state machine* rejects APDU transport after EAC2 failure.
- Whether a deliberately permissive *synthetic regression model* exposes the suspected transition.
- A reproducible and privacy-preserving test report.

It **cannot** prove that an actual nPA accepts a second terminal authentication, nor that the unmodified AusweisApp behaves like the model. For that, obtain an authorized eID test service and separately test the original client and evaluated card behavior.

## Requirements

- JDK 17; Android SDK 35; Android device with NFC and API 26+.
- Gradle 8.9 and Android Gradle Plugin 8.7.3.
- Android Studio can import this project directly. No Gradle wrapper binary is committed; use a locally installed Gradle or generate a wrapper from an approved distribution.

## Build and tests

```sh
gradle testDebugUnitTest
gradle assembleDebug
```

Install the APK and start **Eidelweiss**. Tap **Run offline fault matrix** to run local, deterministic scenarios. Tap **Observe NFC** and hold an NFC document to the phone; **no APDU is sent**. Tap **Share JSON report** to export a redacted report via Android's share sheet.

## Safety and privacy boundaries

The app does not ask for PIN/CAN/PUK, does not log the NFC tag UID, does not transmit APDUs, and requests no Internet permission. No authentication credentials, personal data, card responses, or cryptographic keys are collected. NFC is used in read-only discovery mode (no raw APDU exchange).

## Follow-up research

A valid full EAC security evaluation should use an expressly authorized lab eID server and formal CC-conformant test cards/chips. A new external eID server transaction is *not* equivalent to a new or continued chip-side secure-messaging session, and a public server is not an arbitrary TA-signing oracle.

## Acknowledgements

References: Governikus AusweisApp (EUPL-1.2); BSI TR-03110 parts 2/3; TR-03124; TR-03127. No third-party source code is vendored into this repository.
