# Publishing Kompact

Questa guida raccoglie i passaggi necessari per pubblicare Kompact sia su F-Droid sia sul Google Play Store, evidenziando differenze, prerequisiti e checklist di verifica.

## Prerequisiti comuni
- Tutto il codice deve essere sotto licenza **GNU AGPL-3.0-or-later** (`LICENSE`).
- Aggiorna `versionCode`/`versionName` in `app/build.gradle.kts` e crea un tag Git `vX.Y.Z` coerente.
- Esegui `./gradlew clean assembleRelease` e `./gradlew bundleRelease` dalla stessa macchina per garantire build riproducibili.
- Verifica che l'app non usi dipendenze proprietarie o servizi Google Play.
- Prepara changelog, screenshot e asset grafici condivisi fra gli store.

## Flusso F-Droid
1. **Aggiorna i metadata**
   - Modifica `fdroid/metadata/com.kompact.yml` con gli URL reali (`Repo`, `IssueTracker`, `Changelog`).
   - Inserisci il nuovo `versionName`, `versionCode` e il tag/commit corrispondente.
2. **Verifica locale**
   - Installa `fdroidserver` e `fdroidcl`.
   - Esegui `fdroid lint --metadata-path fdroid/metadata com.kompact` per validare il file.
   - Esegui `fdroid build --server --metadata-path fdroid/metadata com.kompact` per assicurarti che `gradlew assembleRelease` funzioni senza firma.
3. **Invio a fdroiddata**
   - Forka `https://gitlab.com/fdroid/fdroiddata`.
   - Copia `fdroid/metadata/com.kompact.yml` nel fork (stesso percorso) e apri una Merge Request.
   - Includi nel MR: link al tag sorgente, SHA256 di `app-release-unsigned.apk`, log di `fdroid build` verde.
4. **Aggiornamenti futuri**
   - Incrementa `versionCode` e aggiorna `CurrentVersion*` nel metadata.
   - F-Droid ricostruirà autonomamente l'APK, non serve firmarlo.

## Flusso Google Play
1. **Configura la chiave di upload**
   - Genera un keystore dedicato (`keytool -genkeypair ...`).
   - Esporta le variabili d'ambiente richieste dal Gradle script:
     - `KS_PATH`: percorso assoluto del keystore
     - `KS_PASS`: password del keystore
     - `KS_ALIAS`: alias della chiave
     - `KEY_PASS`: password della chiave
2. **Compila e firma**
   - `./gradlew clean assembleRelease bundleRelease`
   - L'APK viene firmato automaticamente se tutte le variabili sono valorizzate; in caso contrario usa `./gradlew signingReport` per verificare.
   - Carica l'`app-release.aab` su Play Console (obbligatorio). L'APK firmato rimane utile per test interni o distribuzioni dirette.
3. **Play Console**
   - Abilita Play App Signing (consigliato) e conserva il certificato di upload.
   - Compila schede "Contenuti dell'app":
     - **Sicurezza dei dati**: dichiara che l'app non raccoglie né condivide dati.
     - **Classificazione contenuti**: produttività/utilità, nessun contenuto sensibile.
     - **Privacy policy**: link al README o a una pagina statica che spieghi che non vengono raccolti dati personali.
   - Crea un "Test interno" prima del rollout in produzione.
4. **Rollout**
   - Allegare changelog (max 500 caratteri per lingua) con le novità principali.
   - Verificare che la firma SHA256 pubblicata coincida con quella generata durante la build.

## QA consigliato prima di ogni release
- Avviare un ciclo di compressione immagini (JPEG, PNG, WebP) e PDF su API 24, 29 e 35.
- Verificare permessi SAF con sorgenti interni/SD.
- Controllare che l'app funzioni offline (modalità aereo) e che la UI Material 3 rispetti chiaro/scuro.
- Eseguire `./gradlew test connectedDebugAndroidTest` su almeno un dispositivo/emulatore.

Seguire questa checklist riduce il tempo di revisione sia per F-Droid sia per Google Play e garantisce coerenza tra build autogenerate e pacchetti caricati manualmente.
