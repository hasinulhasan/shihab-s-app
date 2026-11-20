# M-CHAT-R/F Clinician (Android)

Offline-first, single-clinician Android application (Kotlin, Jetpack Compose, Material 3 + Liquid Glass design system) for administering, scoring, recording, and managing the **M-CHAT-R/F** (Modified Checklist for Autism in Toddlers, Revised with Follow-Up) in **English** and **Bangla (বাংলা)**.

> **Clinical Notice:** This application is a developmental **screening and record-keeping tool only** — it is **NOT** a diagnostic instrument. Scores are never presented as autism probabilities and children are never labeled as autistic.

---

## 1. Architecture & Modules (14 Modules)

| Module | Responsibility |
| --- | --- |
| `:domain-scoring` | Pure-Kotlin deterministic M-CHAT-R/F scoring engine (zero Android imports), instrument JSON schemas, SHA-256 verifier |
| `:core-common` | Language/Theme/Timeout preferences models, `AgeCalculator`, `AssetInstrumentLoader`, `LocaleController` |
| `:core-security` | SQLCipher Android Keystore passphrase wrapping, AES-256-GCM + Argon2id/PBKDF2 cloud backup crypto engine, PIN PBKDF2 hasher, `AppLockManager`, `BiometricAuthHelper`, `AuditHashChain`, `SecureScreenEffect` (`FLAG_SECURE`) |
| `:core-design` | Liquid Glass M3 design system (Haze backdrop blur on API 31+ with solid fallback), bundled `Plus Jakarta Sans` + `Noto Sans Bengali`, accessible `ClinicalAnswerButton`, `StatusChip`, `OutcomeBadge`, `GlassBottomBar`, `AppLockScreen`, `ClinicianReAuthDialog` |
| `:data-local` | SQLCipher-encrypted Room v1 database (11 entities, exported schemas, real migration scaffold), DAOs, repositories, `StatsRepository` (SQL aggregates + de-identified CSV), DataStore UI preferences |
| `:data-sync` | Google Drive v3 Retrofit API (no body logging), Credential Manager + `AuthorizationClient` (`drive.file` scope only), `DriveFolderManager`, `EncryptedSyncWorker` (WorkManager exponential backoff), `DriveRestoreManager` |
| `:feature-home` | Local-time greeting, 6 interactive stat cards, recent screenings with privacy mask toggle, debounced 1s pull-down-and-hold survey trigger, `FilteredScreeningsRoute` |
| `:feature-patients` | Consent gate, `PatientFieldRegistry`, registration/edit with auto/manual ID duplicate detection, exact years/months age + 16–30m warning acknowledgement, search/filters, profile, PIN-gated soft archive |
| `:feature-screening` | Instrument SHA-256 verification gate, unfinished screening resume prompt, 1-item-per-screen questionnaire, auto-advance + Undo, instant autosave, Review All 20 + edit/recalculate, abandon confirmation, PIN-locked Caregiver Hand-Over mode |
| `:feature-followup` | Data-driven Stage 2 Follow-Up interview (only Stage 1 at-risk items), autosave, back navigation, Result screen with official recommendations & non-diagnostic disclaimer, immutable Correction Revision creator, privacy-safe local rescreening reminders |
| `:feature-reports` | A4 PDF generator (`PdfDocument` + `StaticLayout` + bundled `Noto Sans Bengali`) supporting EN, BN, and Bilingual reports; `PdfRenderer` viewer; SAF save; `FileProvider` secure share; client-side encrypted Drive upload |
| `:feature-stats` | Real Room SQL analytics, date presets + custom range, interactive Compose Canvas charts opening filtered lists, explicit outcome definitions, de-identified CSV export |
| `:feature-settings` | Runtime EN/BN switch, Light/Dark/System + Reduce Transparency, Clinician PIN/Biometrics/Auto-Lock, Argon2id Recovery Passphrase setup, Google Drive sync/restore with diff preview, Cloud Console setup guide, Tamper-Evident Audit Log viewer, PIN-gated wipe |
| `:app` | Application shell, Hilt DI + `HiltWorkerFactory`, type-safe Navigation Compose graph, R8 release configuration, bundled verified EN & BN M-CHAT-R/F JSON assets |

---

## 2. Build & Release Instructions

- **Requirements:** JDK 17+ / Kotlin 2.4.21 / AGP 9.1.1 / compileSdk 36 (`minSdk 26`, `targetSdk 36`).
- **Debug build:** `gradle :app:assembleDebug`
- **Release build (R8 enabled, signed):**
  Set signing environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) and run:
  `gradle :app:assembleRelease`
- **Run full unit & Robolectric test suite:**
  `gradle :domain-scoring:test :core-common:testDebugUnitTest :core-security:testDebugUnitTest :data-local:testDebugUnitTest :data-sync:testDebugUnitTest :feature-reports:testDebugUnitTest :app:testDebugUnitTest`

---

## 3. Google Cloud Console Setup Guide (Google Drive Sync)

To enable clinician Google Drive encrypted backups (`M-CHAT Medical Records/{backups,reports,statistics}`):

1. Open **Google Cloud Console** and create or select a Cloud project.
2. Navigate to **APIs & Services → Library** and enable the **Google Drive API**.
3. Open **APIs & Services → OAuth consent screen**:
   - Choose **External** (or Internal for Google Workspace clinicians).
   - Add **ONLY** the non-sensitive/least-privilege scope: `https://www.googleapis.com/auth/drive.file`.
   - **CRITICAL 7-DAY TOKEN EXPIRY WARNING:** Any OAuth consent screen left in **"Testing"** publishing status issues refresh tokens that **expire automatically after 7 days**, causing background Drive sync to fail with `TOKEN_EXPIRED` until re-authorized. You **MUST** either add your clinician Google account under **Test Users** (and re-authorize weekly) OR **publish the OAuth consent screen to Production** so refresh tokens persist.
4. Open **APIs & Services → Credentials → Create Credentials → OAuth client ID**:
   - **Android Client:** Package name `com.aistudio.mchatrf.kvnqxr` + SHA-1 fingerprint of your upload/release signing keystore (`keytool -list -v -keystore <keystore>`).
   - **Web Application Client (optional for Credential Manager Google Sign-In):** Paste the generated Web Client ID into *Settings → Google Drive Encrypted Backup*.

---

## 4. Security Checklist

| Security Control | Status | Implementation Detail |
| --- | --- | --- |
| Encrypted local DB (SQLCipher AES-256) | **PASS** | `DatabasePassphraseProvider` generates random 32-byte key wrapped with Android Keystore AES-256-GCM in `noBackupFilesDir` |
| Cloud backup encryption independent of Keystore | **PASS** | `BackupCryptoEngine` uses random AES-256-GCM DEK wrapped by Argon2id KEK (PBKDF2 fallback) + pre-decrypt SHA-256 integrity hash |
| Encrypt before upload (DB, PDFs, CSVs) | **PASS** | `DriveSyncCoordinator` and `EncryptedSyncWorker` encrypt all payloads client-side before network transit |
| Least-privilege Google Drive scope | **PASS** | `DriveAuthManager` requests ONLY `https://www.googleapis.com/auth/drive.file` |
| Screen capture & recent-apps protection | **PASS** | `FLAG_SECURE` applied in `MainActivity.onCreate` and enforced via `SecureScreenEffect` |
| Android Auto-Backup & Device Transfer disabled | **PASS** | `android:allowBackup="false"`, restrictive `backup_rules.xml` and `data_extraction_rules.xml`, verified by `InstrumentAssetIntegrityTest` |
| App Lock (6-digit PIN + Biometric + Auto-Lock) | **PASS** | PBKDF2-HMAC-SHA256 salted PIN hash (`PinHasher`), `BiometricPrompt`, lifecycle background lock + timeout |
| Zero PHI in logs, notifications, or analytics | **PASS** | No analytics SDKs, no OkHttp body logging, R8 strips `Log.v/d/i`, `RescreenReminderWorker` notification contains zero patient names/IDs |
| Tamper-evident Audit Log | **PASS** | `AuditHashChain` SHA-256 linked sequence verified live in Settings → Audit Log |
| Re-authentication before destructive actions | **PASS** | `ClinicianReAuthDialog` (PIN/Biometric) gates patient Archive/Restore, Wipe All Data, and Caregiver Hand-Over exit |
| Instrument integrity verification | **PASS** | `AssetInstrumentLoader` verifies SHA-256 against `instrument_manifest.json` and refuses screening on mismatch |
| Completed screening immutability | **PASS** | Locked screenings reject direct edits; corrections create versioned `ScreeningRevisionEntity` + `CORRECTION` audit entry |
| Release R8 obfuscation | **PASS** | `isMinifyEnabled = true`, `isShrinkResources = true`, hardened `proguard-rules.pro` |

---

## 5. Manual QA Checklist

1. **First Launch & App Lock:** Configure a 6-digit PIN in Settings → toggle Biometrics → background the app → return and verify lock screen triggers and logs a `LOGIN` audit entry.
2. **Language & Theme:** Switch between English and বাংলা at runtime; verify Noto Sans Bengali renders cleanly and Light/Dark + Reduce Transparency modes update immediately.
3. **Consent & Patient Registration:** Register a child → verify Consent step is mandatory → test duplicate Patient ID warning → enter DOB <16m or >30m and verify the non-blocking acknowledgement checkbox is required.
4. **Questionnaire & Autosave/Resume:** Start a screening → answer 5 items → force-close app → reopen New Survey tab and verify the **Resume Unfinished Screening** card restores exact progress → test **Undo** tap → test **Caregiver Hand-Over Mode** (verify Back button is blocked and clinician PIN is required to exit).
5. **Scoring & Follow-Up:** Test scores `0–2` (Low -> Negative; if <24m verify rescreen reminder button), `3–7` (Moderate -> Stage 2 Follow-Up for at-risk items only; verify 0–1 fail = Negative, ≥2 fail = Positive), and `8–20` (High -> Direct Referral bypassing Follow-Up).
6. **Immutability & Revisions:** On a completed Result screen, tap **Create Correction Revision** → enter reason → verify revision 2 opens without overwriting revision 1.
7. **PDF Reports:** Generate English, Bangla (verify যুক্তবর্ণ conjuncts), and Bilingual PDFs → verify in-app preview, SAF Save, FileProvider Secure Share, and Encrypted Drive Upload queueing.
8. **Home & Pull-Down Gesture:** Verify greeting, all 6 stat cards opening filtered lists, Privacy Mask toggle hiding names, and 1-second pull-down-and-hold shortcut triggering only once without fighting scrolling.
9. **Statistics & CSV:** Verify date presets (`Today`, `7d`, `30d`, `90d`, `This Year`, `Custom`), tap chart segments to open filtered lists, and export de-identified CSV (confirm zero names/contacts inside CSV).
10. **Encrypted Drive Backup & Restore:** Configure Recovery Passphrase → authorize Drive → sync in Airplane Mode (verifies WorkManager queues & retries when online) → test Restore with wrong passphrase (rejected) and valid passphrase (diff summary shown before explicit confirmation).

---

## 6. Known Limitations

- **Clinical Follow-Up Flowcharts:** The uploaded source document (`m chart.pdf`) provided all 20 official Bangla Stage 1 items and official Stage 1/Stage 2 scoring rules verbatim. Detailed item-by-item multi-branch Follow-Up interview probe scripts can be dropped directly into `app/src/main/assets/instrument/*.json` (updating `instrument_manifest.json` SHA-256 hashes) without modifying Kotlin code because the Follow-Up engine is 100% data-driven.
- **Google Drive OAuth Setup:** Requires the clinician's own Google Cloud project OAuth Android Client ID (matching signing SHA-1) as described in Section 3.
- **Keystore Non-Portability:** By design, Android Keystore keys do not transfer across devices or reinstalls; clinicians must safely retain their Recovery Passphrase to restore encrypted Google Drive backups on a new device.
