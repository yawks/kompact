# Kompact - Contesto App e Istruzioni AI

## Contesto dell'App

**Kompact** è un'app Android open-source per la compressione di immagini (JPEG, PNG, WebP) direttamente sul dispositivo. Scritta in Kotlin con Jetpack Compose, offre compressione configurabile con anteprima in tempo reale.

### Caratteristiche principali
- Selezione multipla immagini tramite Storage Access Framework
- Compressione configurabile: qualità, dimensione target, formato output
- Anteprima live lato-a-lato fonte/output
- Supporto Material 3 con tema light/dark
- Esportazione automatica in Pictures/Kompact (configurabile)
- Nessun servizio proprietario - funziona completamente offline
- Formati supportati: JPG, PNG, WEBP, BMP, GIF, TIF, HEIF, HEIC, AVIF, JXL, TELEGRAM_PNG
- Funzioni di editing: Crop, Resize, Rotazione, Filtri colore, Testo sovrapposto, Sfondo personalizzato

### Tecnologie
- **Linguaggio**: Kotlin
- **UI**: Jetpack Compose con Material 3
- **Min SDK**: 24 (Android 7.0+)
- **Target SDK**: 35
- **Gradle**: 8.7+
- **AGP**: 8.5.1
- **JDK**: 17 (配置在 JAVA_HOME)

## Informazioni Importanti per Mantenersi il Contesto

### Struttura del Progetto
```
app/src/main/java/com/kompact/
├── data/              # Repository e logica compressione
│   ├── CompressionRepository.kt    # Compressione finale
│   └── ImagePreviewGenerator.kt     # Generazione anteprime live
├── model/             # Modelli di dominio
│   └── CompressionModels.kt         # Configurazioni (ResizeConfig, etc.)
├── ui/                # ViewModel e Composable
│   ├── KompactScreen.kt             # Schermata principale
│   ├── CompressionViewModel.kt      # State management
│   └── TextEditorDialog.kt          # Editor di testo
├── util/              # Helper riutilizzabili
│   ├── ImageProcessing.kt            # Funzioni applyCrop, applyResize, etc.
│   ├── ImageScaler.kt               # Logica scaling immagini
│   └── ColorMatrixHelper.kt         # Filtri colore
└── KompactApp.kt     # App entry point
```

## Link Utili

### Repository Ufficiali
| Piattaforma | URL | Note |
|------------|-----|------|
| **Forgejo (Naxod)** | `https://git.naxod.com/luca/Kompact` | Repository principale |
| **GitLab (Mirror)** | `https://gitlab.com/naxodlab/kompact` | Mirror per F-Droid |
| **GitLab (NaxodLab)** | `https://gitlab.com/naxodlab` | Organizzazione |

### F-Droid
| Risorsa | URL |
|---------|-----|
| Metadata | `./fdroid/metadata/com.kompact.yml` | Configurazione build F-Droid |
| F-Droid Data Repo | `https://gitlab.com/fdroid/fdroiddata` | Repository metadata F-Droid |
| App Page | `https://f-droid.org/packages/com.kompact/` | Pagina app (in pubblicazione) |

### Documentazione
| File | Descrizione |
|------|-------------|
| `README.md` | Overview, features, requirements |
| `docs/PUBLISHING.md` | Processo di rilascio e QA |
| `Privacy Policy.md` | Policy privacy |
| `LICENSE` | AGPL-3.0-or-later |

### Risorse Esterne **da aggiungere a metadata nativi per scheda store F Droid (non sono ancora presenti in store nonostante ci siano alcuni metadata impostati)
- **Sito Web**: `https://naxod.com/kompact/`
- **Donazioni**: `https://liberapay.com/Naxod/`
- **Email Mantainer**: `lab@naxod.com`

## Comandi Utili

### Build
```bash
# Debug build
./gradlew assembleDebug

# Release build (unsigned)
./gradlew assembleRelease

# Bundle per Google Play
./gradlew bundleRelease

# Test
./gradlew test
./gradlew connectedDebugAndroidTest
```

### F-Droid Build Test
```bash
# Da /data/f-droid
fdroid update -c
fdroid build -v com.kompact
```

## Note di Rilascio

### Processo Standard
1. Aggiornare `versionName` e `versionCode` in `app/build.gradle.kts`
2. Aggiornare `fdroid/metadata/com.kompact.yml` con nuova versione e commit
3. Aggiornare `CHANGELOG` o `README.md` con le novità
4. Eseguire test: `./gradlew test`
5. Build release: `./gradlew assembleRelease`
6. Testare APK unsigned su dispositivi reali
7. Creare tag Git: `git tag -a vX.Y.Z -m "Version X.Y.Z"`
8. Push tag: `git push origin vX.Y.Z`
9. Aggiornare merge request su fdroiddata

## Mantenersi il Contesto tra Sessioni

### Punti Chiave da Ricordare
5. **Metadata F-Droid punta al repo ufficiale**: `https://git.naxod.com/luca/Kompact`
6. **GitLab mirror per CI F-Droid**: `https://gitlab.com/naxodlab/kompact` (solo per pipeline)
6. **Nessun file di firma nel repo**: i build ufficiali sono firmati solo dal maintainer

### Pattern Ricorrenti
- Tutte le funzioni `apply*` in `ImageProcessing.kt` seguono lo schema: `Bitmap -> Bitmap`
- Le configurazioni sono immutable (data class)
- Lo state è gestito tramite ViewModel con Compose
- Le immagini temporanee vanno in cacheDir


## Regole del Progetto

1. **Niente commit di file firmati** - I build ufficiali rimangono unsigned nel repo
2. **Niente dipendenze proprietarie** - Mantiene compatibilità F-Droid
3. **Tutti i cambiamenti devono passare i test** - `./gradlew test` prima di merge
4. **VersionCode deve essere incrementato** - Mai riutilizzare un versionCode
5. **Tag solo dal maintainer** - Garantisce catena di custodia unica
6. **ai-istruction.md NON va in Git** - Contiene info di contesto private per AI assistant

---
*Ultimo aggiornamento: 2026-07-11*
*Mantainer: Naxod (lab@naxod.com)*
