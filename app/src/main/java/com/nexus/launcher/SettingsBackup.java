package com.nexus.launcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local settings backup: exports every preference (home layout included) to a JSON document
 * chosen with the Android file picker and restores it from a user selected file.
 * Nothing leaves the device and no network permission is required.
 */
public final class SettingsBackup {

    public static final int REQUEST_EXPORT = 7601;
    public static final int REQUEST_IMPORT = 7602;

    private static final int MAX_BACKUP_BYTES = 2 * 1024 * 1024;

    private SettingsBackup() {
    }

    public static void export(MainActivity activity, LauncherSettings settings) {
        if (settings.exportJson() == null) {
            Toast.makeText(activity, "Impossibile preparare il backup", Toast.LENGTH_SHORT).show();
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "nexus-backup-" + stamp + ".json");
        try {
            activity.startActivityForResult(intent, REQUEST_EXPORT);
        } catch (Exception e) {
            Toast.makeText(activity, "Nessun selettore di file disponibile", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public static void importBackup(MainActivity activity, LauncherSettings settings) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            activity.startActivityForResult(intent, REQUEST_IMPORT);
        } catch (Exception e) {
            Toast.makeText(activity, "Nessun selettore di file disponibile", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    /**
     * Handles the file picker result. Returns true when the request belonged to the backup flow.
     */
    public static boolean handleActivityResult(MainActivity activity, LauncherSettings settings,
                                               int requestCode, int resultCode, Intent data,
                                               Runnable onImported) {
        if (requestCode != REQUEST_EXPORT && requestCode != REQUEST_IMPORT) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            writeBackup(activity, settings, uri);
        } else {
            readBackup(activity, settings, uri, onImported);
        }
        return true;
    }

    private static void writeBackup(MainActivity activity, LauncherSettings settings, Uri uri) {
        String json = settings.exportJson();
        if (json == null) {
            Toast.makeText(activity, "Impossibile preparare il backup", Toast.LENGTH_SHORT).show();
            return;
        }
        OutputStream output = null;
        try {
            output = activity.getContentResolver().openOutputStream(uri, "wt");
            if (output == null) {
                Toast.makeText(activity, "Impossibile scrivere il file", Toast.LENGTH_SHORT).show();
                return;
            }
            output.write(json.getBytes("UTF-8"));
            output.flush();
            Toast.makeText(activity, "Backup salvato", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Impossibile scrivere il backup", Toast.LENGTH_SHORT).show();
        } finally {
            closeQuietly(output);
        }
    }

    private static void readBackup(MainActivity activity, LauncherSettings settings, Uri uri,
                                   Runnable onImported) {
        InputStream input = null;
        try {
            input = activity.getContentResolver().openInputStream(uri);
            if (input == null) {
                Toast.makeText(activity, "Impossibile leggere il file", Toast.LENGTH_SHORT).show();
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BACKUP_BYTES) {
                    Toast.makeText(activity, "File troppo grande per un backup di Nexus",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                buffer.write(chunk, 0, read);
            }
            String json = new String(buffer.toByteArray(), "UTF-8");
            if (settings.importJson(json)) {
                Toast.makeText(activity, "Backup ripristinato", Toast.LENGTH_SHORT).show();
                if (onImported != null) {
                    onImported.run();
                }
            } else {
                Toast.makeText(activity, "File di backup non valido", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(activity, "Impossibile ripristinare il backup", Toast.LENGTH_SHORT)
                    .show();
        } finally {
            closeQuietly(input);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // nothing to do
        }
    }
}
