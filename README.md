# Nexus

Launcher Android scritto in Java, con estetica Pixel e dettagli iOS. Package
`com.nexus.launcher`, Android 10 (API 29) e successivi.

## Cosa fa

- **Home a pagine**: pagine scorrevoli con pagina centrale predefinita, drag and drop delle
  icone tra posizioni e pagine, cartelle create trascinando un'icona su un'altra e widget reali
  tramite `AppWidgetHost` (scelta, configurazione, ridimensionamento, spostamento, rimozione).
- **Swipe in stile iOS**: la pagina segue il dito senza ritardo, conserva l'inerzia del gesto,
  si aggancia alla pagina più vicina e rimbalza oltre la prima e l'ultima; dock fermo e icone
  che si spostano con la pagina.
- **Cassetto app**: ricerca in tempo reale, ordine alfabetico o di installazione, app
  nascondibili senza disinstallarle.
- **Dock**: fino a cinque applicazioni scelte dalle impostazioni.
- **Aspetto**: tema chiaro, scuro o automatico, colore accento, forma e dimensione delle icone,
  scala del testo, contrasto, sfondo predefinito / di sistema / da galleria, aspetti delle cartelle.
- **Modalità**: Nexus e Nexus Lite nello stesso APK. Lite usa una home essenziale con le
  scorciatoie di telefono, messaggi, fotocamera e impostazioni, icone grandi, cassetto con campo
  "Cerca app" e nessun fronzolo, mantenendo lo swipe fluido con inerzia e rimbalzo.
- **Animazioni**: frequenza Automatico, Standard 60 Hz o Veloce 120 Hz, applicata alla finestra
  solo se lo schermo supporta davvero quel modo, più effetto di cambio pagina configurabile.
- **Dati locali**: backup e ripristino in JSON tramite il selettore file di Android, nessuna rete.

## Nexus UI — impostazioni

Le impostazioni sono una dashboard a schede con ricerca e dodici sezioni animate:

| Sezione | Contenuto |
| --- | --- |
| Personalizzazione | Tema, colore accento, forma e dimensione icone, etichette, sfondo, anteprima |
| Schermata Home | Colonne, icone, etichette, pagine, dock, cartelle, ripristino layout |
| Schermo e luminosità | Tema, contrasto, scala del testo, scala icone, scorciatoia alle impostazioni display |
| Animazioni | Attiva/disattiva, frequenza Automatico/60/120 Hz, effetto cambio pagina |
| Applicazioni | Ordine del cassetto, comportamento della ricerca, visibilità delle app |
| Widget | Aggiunta, elenco dei widget collocati, ridimensionamento, spostamento, rimozione |
| Prestazioni | Scelta Nexus / Nexus Lite con differenze spiegate |
| Confronto versioni | Tabella Funzione / Nexus Lite / Nexus divisa per categorie |
| Privacy e sicurezza | Dati locali, permessi, privacy policy, impostazioni permessi di sistema |
| Sistema | Backup locale, ripristini, launcher predefinito, versione e build |
| Informazioni su Nexus | Versione, sviluppatore, GPL v3, licenze di terze parti, supporto, repository |
| Informazioni sul dispositivo | Dispositivo, hardware, memoria, schermo, batteria e dati Nexus, con aggiornamento e copia dei dettagli |

Ogni controllo è collegato a `LauncherSettings` e tutte le modifiche sono persistenti.

## Confronto versioni

La sezione **Confronto versioni** mostra una tabella a tre colonne (Funzione, Nexus Lite,
Nexus) divisa in otto categorie: home, personalizzazione, widget, animazioni, prestazioni,
cassetto e ricerca, aspetto e display. I simboli sono `✓` disponibile, `◐` disponibile con
limiti e `—` non disponibile. Le voci non implementate sono dichiarate come tali: la tabella
viene aggiornata insieme al codice, non è un'immagine statica.

## Fluidità

Lo scorrimento della home è gestito da un pager che segue il dito uno a uno, calcola la velocità
con `VelocityTracker`, proietta la posizione al rilascio e si aggancia alla pagina più vicina con
un'interpolazione decelerata; ai bordi applica una resistenza elastica. Le pagine vengono create
solo quando entrano nella finestra visibile, le icone arrotondate sono memorizzate per dimensione,
lo sfondo di sistema viene decodificato una sola volta e quello personale fuori dal thread
principale, gli intent di avvio sono in cache e il cassetto filtra con debounce. L'orologio si
aggiorna sul minuto invece che a intervalli fissi. Nexus Lite riduce ombre, sfondo e animazioni
decorative ma mantiene lo swipe con inerzia.

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
