# Nexus

Nexus è un launcher Android reale, scritto interamente in Java, con estetica Pixel e dettagli
iOS: moderno, essenziale e fluido. Non è un sistema operativo simulato e non è una schermata
dimostrativa: sostituisce la home del dispositivo.

## Cosa fa

- **Launcher predefinito**: `MainActivity` dichiara i filtri `HOME` e `LAUNCHER`, quindi Android
  la propone come home. Dalle impostazioni si apre direttamente la richiesta del ruolo HOME.
- **Home a pagine**: pagine scorrevoli con pagina centrale predefinita, drag and drop delle
  icone tra posizioni e pagine, cartelle create trascinando un'icona su un'altra e widget reali
  tramite `AppWidgetHost` (scelta, configurazione, ridimensionamento, rimozione).
- **Icone arrotondate**: le icone di sistema vengono ritagliate in un quadrato arrotondato
  (stile Pixel) e messe in cache per non essere ricalcolate.
- **Avvio applicazioni**: tap su icona, dock o risultato di ricerca.
- **Dock personalizzabile**: fino a 5 app scelte dall'utente e salvate nelle preferenze.
- **Barra di ricerca**: apre il cassetto app con la ricerca in tempo reale già filtrata.
- **Menu delle applicazioni**: cassetto a scomparsa con ricerca e tutte le app installate.
- **Sfondo personalizzato**: immagine dalla galleria (copiata in memoria interna), sfondo di
  sistema oppure gradiente predefinito Nexus.
- **Impostazioni dedicate**: pannello a scomparsa con tema, versione launcher, dock, sfondo,
  launcher predefinito e informazioni.
- **Tema chiaro / scuro**: sistema, chiaro o scuro, applicato a testi, superfici e barre di sistema.
- **Nexus Lite / Nexus**: scelta al primo avvio e modificabile in seguito; la preferenza è salvata.
- **Animazioni**: apertura e chiusura del cassetto app e del pannello impostazioni.
- **Permessi**: `QUERY_ALL_PACKAGES` e `<queries>` per elencare le app, `SET_WALLPAPER`,
  `RECEIVE_BOOT_COMPLETED`; nessun permesso runtime invasivo.

## Requisiti

- Android 10 (API 29) o superiore.
- Java 17 per la compilazione, Gradle 8.9, Android Gradle Plugin 8.7.3, compileSdk 35.

## Compilare

```bash
# con JDK 17 e Android SDK 35 installati
gradle --no-daemon assembleDebug     # APK di debug
```

APK generato in `app/build/outputs/apk/debug/app-debug.apk`.

### Firmare la release in locale

La firma della build release legge tutto dall'ambiente, quindi il keystore non sta nel repository:

```bash
export KEYSTORE_FILE=/percorso/nexus-release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=nexus
export KEY_PASSWORD=...
gradle --no-daemon assembleRelease
```

APK firmato in `app/build/outputs/apk/release/app-release.apk`. Senza queste variabili
`assembleRelease` produce un APK non firmato (`app-release-unsigned.apk`) e la CI non pubblica release.

## APK automatico e release firmata

Il workflow `.github/workflows/android.yml` compila a ogni push e pull request. Con i segreti di
firma configurati costruisce `assembleRelease`, verifica la firma con `apksigner`, rifiuta un APK
`debuggable` e, sui push a `main`, crea o aggiorna la release `v<versionName>` allegando
`Nexus-<versionName>.apk`: il download è diretto, senza passare dagli artifact.

Segreti da aggiungere in **Settings → Secrets and variables → Actions**:

| Segreto | Contenuto |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore `.jks` codificato in base64 |
| `ANDROID_KEYSTORE_PASSWORD` | password del keystore |
| `ANDROID_KEY_ALIAS` | alias della chiave |
| `ANDROID_KEY_PASSWORD` | password della chiave |

Generazione del keystore e codifica base64:

```bash
keytool -genkeypair -keystore nexus-release.jks -alias nexus -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Nexus, O=Nexus, C=IT"
base64 -w0 nexus-release.jks > nexus-release.jks.b64   # su macOS: base64 nexus-release.jks
```

Conserva il keystore e le password: senza la stessa chiave gli aggiornamenti futuri non possono
sovrascrivere l'app installata. Senza `ANDROID_KEYSTORE_BASE64` la CI compila solo debug, carica
l'artifact `NEXUS-apk` e salta la pubblicazione della release.

## Struttura

| File | Ruolo |
| --- | --- |
| `MainActivity.java` | Activity home, barre di sistema, avvio app, richiesta launcher predefinito, icone arrotondate |
| `HomeScreen.java` | Layout home: orologio, ricerca, griglia, dock, cassetto app, sfondo |
| `HomeGrid.java` | `GridView` con adapter delle app e tile "Tutte le app" |
| `SearchBar.java` | Campo di ricerca a pillola con pulsante di cancellazione |
| `DockBar.java` | Dock con massimo 5 app e click per singola voce |
| `SettingsPanel.java` | Pannello impostazioni a scomparsa |
| `SettingsDialogs.java` | Dialoghi tema, versione, dock, informazioni, launcher predefinito |
| `WallpaperPickerDialog.java` | Scelta sfondo da galleria, sistema o predefinito |
| `LauncherSettings.java` | Preferenze persistite e palette colori |
| `AppInfo.java` | Metadati app con caricamento icona lazy |
| `BootReceiver.java` | Ricevitore di avvio |
| `Ui.java` | Utility per dp e drawable arrotondati |
