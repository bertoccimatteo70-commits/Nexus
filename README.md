# Nexus

Nexus è un launcher Android reale, scritto interamente in Java, con estetica Pixel e dettagli
iOS: moderno, essenziale e fluido. Non è un sistema operativo simulato e non è una schermata
dimostrativa: sostituisce la home del dispositivo.

## Cosa fa

- **Launcher predefinito**: `MainActivity` dichiara i filtri `HOME` e `LAUNCHER`, quindi Android
  la propone come home. Dalle impostazioni si apre direttamente la richiesta del ruolo HOME.
- **Home con griglia app**: griglia scorrevole delle applicazioni installate, con numero di
  colonne e dimensione delle icone calcolati dalla larghezza dello schermo.
- **Icone arrotondate**: le icone di sistema vengono ritagliate in un quadrato arrotondato
  (stile Pixel) e messe in cache per non essere ricalcolate.
- **Avvio applicazioni**: tap su icona, dock o risultato di ricerca.
- **Dock personalizzabile**: fino a 5 app scelte dall'utente e salvate nelle preferenze.
- **Barra di ricerca**: filtra la griglia della home in tempo reale.
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
gradle --no-daemon assembleDebug
```

APK generato in `app/build/outputs/apk/debug/app-debug.apk`.

## APK automatico con GitHub Actions

Il workflow `.github/workflows/android.yml` compila a ogni push (qualsiasi branch) e a ogni
pull request, e carica l'APK come artifact scaricabile:

1. Apri la scheda **Actions** del repository.
2. Seleziona l'esecuzione **Build NEXUS APK**.
3. Nella sezione **Artifacts** scarica `NEXUS-debug-apk`.

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
