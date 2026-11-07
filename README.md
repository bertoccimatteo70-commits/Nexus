# Nexus

Launcher Android scritto in Java, con estetica Pixel e dettagli iOS. Package
`com.nexus.launcher`, Android 10 (API 29) e successivi.

## Cosa fa

- **Home a pagine**: pagine scorrevoli con pagina centrale predefinita, drag and drop delle
  icone tra posizioni e pagine, cartelle create trascinando un'icona su un'altra e widget reali
  tramite `AppWidgetHost` (scelta, configurazione, ridimensionamento, spostamento, rimozione).
- **Cassetto app**: ricerca in tempo reale, ordine alfabetico o di installazione, app
  nascondibili senza disinstallarle.
- **Dock**: fino a cinque applicazioni scelte dalle impostazioni.
- **Aspetto**: tema chiaro, scuro o automatico, colore accento, forma e dimensione delle icone,
  scala del testo, contrasto, sfondo predefinito / di sistema / da galleria, aspetti delle cartelle.
- **Modalità**: Nexus e Nexus Lite nello stesso APK; Lite disattiva le animazioni e alleggerisce
  rendering e sfondo.
- **Dati locali**: backup e ripristino in JSON tramite il selettore file di Android, nessuna rete.

## Nexus UI — impostazioni

Le impostazioni sono una dashboard a schede con ricerca e dieci sezioni animate:

| Sezione | Contenuto |
| --- | --- |
| Personalizzazione | Tema, colore accento, forma e dimensione icone, etichette, sfondo, anteprima |
| Schermata Home | Colonne, icone, etichette, pagine, dock, cartelle, ripristino layout |
| Schermo e luminosità | Tema, contrasto, scala del testo, scala icone, scorciatoia alle impostazioni display |
| Animazioni | Attiva/disattiva, velocità, effetto cambio pagina |
| Applicazioni | Ordine del cassetto, comportamento della ricerca, visibilità delle app |
| Widget | Aggiunta, elenco dei widget collocati, ridimensionamento, spostamento, rimozione |
| Prestazioni | Scelta Nexus / Nexus Lite con differenze spiegate |
| Privacy e sicurezza | Dati locali, permessi, privacy policy, impostazioni permessi di sistema |
| Sistema | Backup locale, ripristini, launcher predefinito, versione e build |
| Informazioni su Nexus | Versione, sviluppatore, GPL v3, licenze di terze parti, supporto, repository |

Ogni controllo è collegato a `LauncherSettings` e tutte le modifiche sono persistenti.

## Compilare

```bash
gradle --no-daemon assembleDebug     # APK di debug
```

APK in `app/build/outputs/apk/debug/app-debug.apk`.

### Firmare la release in locale

La firma legge tutto dall'ambiente, quindi il keystore non sta nel repository:

```bash
export KEYSTORE_FILE=/percorso/nexus-release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=nexus
export KEY_PASSWORD=...
gradle --no-daemon assembleRelease
```

APK firmato in `app/build/outputs/apk/release/app-release.apk`. Senza queste variabili
`assembleRelease` produce `app-release-unsigned.apk`.

## APK automatico e release

`.github/workflows/android.yml` compila a ogni push e pull request. Con i segreti di firma
costruisce `assembleRelease`, verifica la firma con `apksigner`, rifiuta un APK `debuggable` e,
sui push a `main`, crea o aggiorna la release `v<versionName>` con `Nexus-<versione>.apk` e il
suo checksum SHA-256.

Senza segreti compila `assembleDebug` e pubblica una release **etichettata DEBUG** con
`Nexus-<versione>-debug.apk`: non è una build di produzione.

Segreti da aggiungere in **Settings → Secrets and variables → Actions**:

| Segreto | Contenuto |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore `.jks` codificato in base64 |
| `ANDROID_KEYSTORE_PASSWORD` | password del keystore |
| `ANDROID_KEY_ALIAS` | alias della chiave |
| `ANDROID_KEY_PASSWORD` | password della chiave |

```bash
keytool -genkeypair -keystore nexus-release.jks -alias nexus -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Nexus, O=Nexus, C=IT"
base64 -w0 nexus-release.jks > nexus-release.jks.b64   # macOS: base64 nexus-release.jks
```

Conserva keystore e password: gli aggiornamenti futuri devono essere firmati con la stessa chiave.

## Privacy

Nexus non ha il permesso `INTERNET`: preferenze, layout e sfondo restano nella memoria privata
dell'app e i backup vengono scritti solo dove l'utente sceglie. Nessun analytics, nessun account.

## Licenza

GNU General Public License v3.0 (GPL-3.0-only). Vedi [LICENSE](LICENSE).
