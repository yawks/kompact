# Kompact

Kompact è un'app Android open-source scritta in Kotlin e Jetpack Compose che consente di comprimere immagini (JPEG, PNG, WebP) e documenti PDF in modo rapido e trasparente, pensata per una distribuzione privacy-friendly su F-Droid.

## Funzionalità principali
- Selezione multipla di immagini o PDF tramite Storage Access Framework.
- Parametri di compressione personalizzabili (qualità, dimensioni massime, formato di output, DPI PDF).
- Risultati riepilogati con dimensioni originali/compresse e stato dell'operazione.
- UI moderna con Material 3 e supporto chiaro/scuro.
- Salvataggio automatico nella cartella Immagini di sistema (Pictures/Kompact) con possibilità di scegliere un'altra destinazione.
- Anteprima affiancata original/output e controlli percentuale/dimensione sempre visibili e sincronizzati.

## Design
- Palette FDroid-inspired con primario verde smeraldo (#1C8F62), secondario denim (#2F5978) e accento arancio (#B55300) per un look pulito ma innovativo.
- Contrasti AA mantenuti fra testo/iconografia e sfondi chiaro/scuro per un'esperienza accessibile.

## Requisiti
- Android Studio Ladybug o successiva.
- Android Gradle Plugin 8.5+, Gradle 8.7.
- JDK 21 (consigliato) o JDK 17 configurato tramite `JAVA_HOME`.
- SDK Android 35, minSdk 24.

### Configurazione SDK locale
Definisci il percorso dell'SDK creando un file `local.properties` nella root del progetto:

```
sdk.dir=C:\Users\<nome>\AppData\Local\Android\Sdk
```

Sostituisci il percorso con quello effettivo sul tuo sistema oppure imposta la variabile d'ambiente `ANDROID_HOME`.

## Avvio rapido
```bash
# dalla cartella del progetto
./gradlew tasks           # opzionale, verifica wrapper
./gradlew assembleDebug   # compila l'app
./gradlew connectedDebugAndroidTest  # esegue i test strumentali (richiede dispositivo/emulatore)
```
Installa l'APK risultante (`app/build/outputs/apk/debug/app-debug.apk`) su un dispositivo o emulatore Android.

Per la build destinata a F-Droid esegui invece:

```bash
./gradlew assembleRelease   # genera app-release-unsigned.apk
apksigner sign --ks <keystore> --out app-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
apksigner verify app-release.apk
```
La fase di firma è separata così da permettere a F-Droid di riprodurre l'APK e firmarlo con la propria chiave.

## Struttura
- `app/src/main/java/com/kompact/data` contiene il repository locale con la logica di compressione.
- `app/src/main/java/com/kompact/ui` ospita ViewModel e composable principali.
- `app/src/main/java/com/kompact/model` espone i modelli condivisi.
- `app/src/main/java/com/kompact/util` racchiude helper riutilizzabili.

## Distribuzione F-Droid
Consulta `docs/PUBLISHING.md` per la checklist completa di invio a `fdroiddata`.
- Nessuna dipendenza proprietaria o non libera (solo librerie AndroidX, Compose, Material Components e Kotlin).
- `android.enableJetifier=false` e assenza di binari chiusi garantiscono una catena di build riproducibile.
- Non viene usato Google Play Services né SDK di tracking/analytics.
- La build release rimane non firmata per consentire a F-Droid di applicare la propria firma.
- Minification disattivata nel build type `release` per ridurre il carico di R8 e facilitare il diff degli APK.
- Il progetto può essere compilato con `fdroid build` usando un metadata `build.gradle` standard senza patch aggiuntive.
- Proprietà `android.suppressUnsupportedCompileSdk=35` già impostata in `gradle.properties` per mantenere l'output di build pulito.
- È incluso un template `fdroid/metadata/com.kompact.yml` pronto da copiare nel repository metadata ufficiale.

### Requisiti per il metadata F-Droid
- Usa `gradlew assembleRelease` senza ulteriori script.
- Copia gli artefatti da `app/build/outputs/apk/release/`.
- Indica licenza `AGPL-3.0-or-later` e sorgente `https://github.com/<tuo-utente>/Kompact` (aggiorna l'URL una volta pubblicato).
- Aggiorna `Repo`, `IssueTracker` e `Changelog` nel file `fdroid/metadata/com.kompact.yml` con gli URL reali al momento dell'invio della merge request su `fdroiddata`.

## Distribuzione Google Play
Passaggi dettagliati e checklist aggiuntive sono descritte in `docs/PUBLISHING.md`.
- Genera l'Android App Bundle con `./gradlew bundleRelease` (richiesto da Google Play) oppure usa `Generate Signed Bundle/APK` da Android Studio.
- Abilita Play App Signing caricando l'AAB firmato con la tua chiave di upload; Play gestirà la chiave di distribuzione.
- Configura un keystore dedicato all'upload e definisci le variabili d'ambiente `KS_PATH`, `KS_ALIAS`, `KS_PASS`, `KEY_PASS` prima di firmare (non salvare i valori nel repository).
- Compila la scheda Store Listing con le stesse informazioni fornite a F-Droid (descrizione breve/lunga, screenshot 1080x1920, grafica 1024x500, icone adaptive).
- Allegati suggeriti: changelog in formato `What's new`, privacy policy statica (non sono raccolti dati personali) e link alla repository.
- Prima di inviare in produzione crea almeno un Test interno per verificare la firma e i permessi SAF.

### Checklist di pubblicazione
- Allinea `versionCode`/`versionName` in `app/build.gradle.kts` ad ogni release e crea un tag `vX.Y.Z`.
- Esegui `./gradlew clean assembleRelease` e `./gradlew bundleRelease` dalla stessa macchina usata per firmare.
- Firma `app-release-unsigned.apk` solo per Play (F-Droid richiede il pacchetto non firmato) e conserva SHA256 degli artefatti.
- Aggiorna `fdroid/metadata/com.kompact.yml` e `fastlane/metadata` (se usato) con i nuovi changelog.
- Pubblica il codice sorgente e gli asset grafici nello stesso tag per garantire riproducibilità.

## Licenza
Kompact è distribuito sotto licenza **GNU Affero General Public License v3.0 o successive**. Consulta `LICENSE` per i dettagli e includi sempre un link alla sorgente quando redistribuisci eseguibili o fork.
