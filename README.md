# Churn Predictor (Android)

[![ci](https://github.com/jayraj0975/churn-predictor-android/actions/workflows/ci.yml/badge.svg)](https://github.com/jayraj0975/churn-predictor-android/actions/workflows/ci.yml)

A native Android app (Java) that predicts how likely a telecom customer is to leave. Describe the
customer (tenure, bill, contract, services, payment, household) and it shows the churn probability,
a risk band, the factors raising and lowering the risk, and a suggested retention step. Everything is
computed **on the device**, with no network and no backend.

**Try it:** download the signed APK from the [latest release](https://github.com/jayraj0975/churn-predictor-android/releases/latest) and install it on an Android phone (7.0+). Android will ask you to allow installing from that source. **If you installed version 1.0**, uninstall it first: 1.0 was signed with a temporary debug key that no longer exists, so Android will refuse to update it in place. From 1.1 on, releases are signed with one stable key and update normally.

Part of a small set: [`customer-churn-analysis`](https://github.com/jayraj0975/customer-churn-analysis) (a broader model comparison),
[`churnapp`](https://github.com/jayraj0975/churnapp) (the web app) and this Android client. The web app and this app serve **the same
logistic-regression model**: `model/model.json` here is a byte-for-byte copy of the web app's `src/lib/model.json`. The analysis repo compares
models and its cross-validation selects a random forest, which is **not** what either app serves.

## The model

A logistic regression fitted on IBM's public Telco Customer Churn data
(7,032 customers, 27% churn) and scored on a 1,407-customer held-out
test set: **ROC-AUC 0.834** (95% interval 0.811 to 0.856), well calibrated, so
"70%" means about 70 in 100 similar customers left. It was trained unweighted on purpose, which keeps the
probabilities honest. Driver figures are the model's associations, not proven causes.

`model/model.json` is that model exported from the web app's trainer. `tools/generate_model.py` turns it into
plain Java constants (`ModelData.java`) and the test fixtures, so scoring is
`sigmoid(intercept + sum(coefficient x value))` in pure Java. The model's provenance (version, training commit, dataset SHA-256,
coefficient and feature-schema hashes, scikit-learn version) is generated into `ModelData` too and shown at the bottom of the screen.

The money line is **modeled exposure**, not observed loss: annual billing times the predicted churn probability. The data has no
revenue history, so it is not lifetime value and not money a business is guaranteed to keep.

## Verified

- **8 unit tests** run on a plain JVM: the Java model matches **scikit-learn's own predictions to 1e-9**
  on eight real customers; probabilities move the way the data shows (longer contract, longer tenure and automatic
  payment lower risk); the model is calibrated on the held-out set; bands, driver signs and the expected-loss
  arithmetic are checked.
- **Builds** with Gradle 9.7.1, Android Gradle Plugin 9.4.1 and JDK 17 (`assembleDebug`, and a signed `assembleRelease`), and **Android lint passes** with errors set to fail the build. `compileSdk` and `targetSdk` are both 36.
- CI regenerates the model constants and fails if they differ from what is committed.

**Run on emulators.** I installed the app on an Android 14 (API 34) and an Android 16 (API 36) emulator (x86_64, hardware-accelerated):
it launches without a crash on both, and the on-screen probability matches an independent calculation from `model/model.json`
(72.0% for the default customer; choosing a two-year contract in the UI recomputes it to 40.2% and moves the band to Moderate).
Raising `targetSdk` to 36 turns on enforced edge-to-edge drawing on Android 15+, and the first build on the API 36 emulator
**did** break: the title and the top of the card sat under the app bar and the last controls under the gesture bar. The screen now opts in
to edge-to-edge explicitly and pads by the system-bar insets (the top inset AppCompat passes down already includes the app bar), and
was re-checked at the top and bottom of the scroll on both versions. It has **not** been tried on a physical phone.

| Default customer | Two-year contract selected |
|---|---|
| ![Default](docs/screenshot-default.png) | ![Two year](docs/screenshot-two-year.png) |

## Build and run

```bash
./gradlew testDebugUnitTest          # model tests
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
```
Or open the folder in Android Studio and run it. Needs JDK 17 and the Android SDK (API 36 platform).

A signed release build reads its key from the environment, so no key or password is in the repository:
`CHURN_KEYSTORE`, `CHURN_KEYSTORE_PASSWORD` and (optionally) `CHURN_KEY_ALIAS`, then `./gradlew assembleRelease`. Without
`CHURN_KEYSTORE` the release build is produced unsigned.

To update the model: replace `model/model.json` with a new export, then `python3 tools/generate_model.py`.

## Structure

```
app/src/main/java/com/jayraj/churnpredictor/
  ChurnModel.java     scoring, drivers, risk bands (no Android imports)
  ModelData.java      GENERATED coefficients and metrics
  MainActivity.java   the one screen
app/src/test/         JVM unit tests and generated fixtures
model/model.json      the exported model
tools/generate_model.py
```

## License

MIT
