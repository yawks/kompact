dopo vari tentativi di update su https://git.naxod.com/luca/Kompact e https://gitlab.com/naxodlab/kompact
l'update da 1.1.0 a 1.1.1 non è stato ancora triggerato da fdroid bot, e ho ricevuto diversi commenti dubbiosi
dai dev di f droid:
Hmmm, so you pushed your repo to fdroiddata.
What do you want to do? Why do you change the repo, the license, remove some versions, change some versions and add the description?
You're on autoupdate, it will be picked up by our scripts.
if you want to change source repo @naxodlab explain this in https://git.naxod.com/luca/Kompact first

risolvi considerando che la versione 1.1.0 è stata aggiornata da bot nativamente e senza problemi, forse ho fatto confusione
con 1.1.1 su https://gitlab.com/naxodlab/kompact pushando repo?
considera che voglio mantenere https://git.naxod.com/luca/Kompact come repo ufficiale e usare https://gitlab.com/naxodlab/kompact
solo per quanto utile a fdroid per pubblicazione.

sistema e procedi
--------------------
scrivi inoltre output riassuntivo qui sotto (modifiche, considerazioni ecc..) non troppo verbose ma precise e utili

---

## Output Riassuntivo - 11 Luglio 2026

### Problema Identificato
Il bot F-Droid non ha triggerato l'aggiornamento a v1.1.1 per due motivi principali:
1. **Metadata sbagliato**: Il file `metadata/com.kompact.yml` in fdroiddata puntava a `gitlab.com/naxodlab/kompact` invece che al repo ufficiale `git.naxod.com/luca/Kompact`
2. **Tag v1.1.1 inconsistente**: Su origin, il tag v1.1.1 puntava a commit `e3f0ebc8` (fix pipeline) invece che a `53633b72bd` (feat exif, bump version 1.1.1)

### Azioni Intraprese
1. **Aggiornato metadata F-Droid** (`fdroiddata/metadata/com.kompact.yml`):
   - `SourceCode`: gitlab.com -> git.naxod.com/luca/Kompact
   - `Repo`: gitlab.com -> git.naxod.com/luca/Kompact.git
   - `IssueTracker`: gitlab.com -> git.naxod.com/luca/Kompact/issues
   - `Changelog`: gitlab.com -> git.naxod.com/luca/Kompact/releases

2. **Sincronizzato tag v1.1.1 su origin**:
   - Eliminato tag v1.1.1 sbagliato (puntava a e3f0ebc8)
   - Pushato tag v1.1.1 corretto (punta a 53633b72bd)

3. **Aggiunta documentazione** in `README.md`:
   - Sezione "F-Droid publication" che chiarisce che il repo ufficiale è git.naxod.com
   - GitLab usato solo come mirror per CI F-Droid

### Stato Finale
- **origin (git.naxod.com)**: master a 53633b72bd, tag v1.1.1 a 53633b72bd ✓
- **gitlab (gitlab.com)**: tag v1.1.1 a 53633b72bd ✓
- **Metadata F-Droid**: punta a git.naxod.com ✓
- **Auto-update**: F-Droid bot ora troverà automaticamente i tag da repo ufficiale

### Considerazioni
- La versione 1.1.0 era stata buildata correttamente perché il tag esisteva e puntava al commit giusto su entrambi i repo
- Il problema v1.1.1 era provocato da: (a) metadata che puntava a repo sbagliato, (b) tag inconsistente su origin
- La soluzione mantiene git.naxod.com come fonte di verità unica, usando gitlab solo come supporto per F-Droid
- Il commit 53633b72bd contiene il bump version 1.1.1 (versionCode 15) e feature EXIF Position