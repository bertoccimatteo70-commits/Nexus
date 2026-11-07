package com.nexus.launcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lets the user choose a custom launcher background.
 */
public class WallpaperPickerDialog {

    public static final int REQUEST_PICK_IMAGE = 7301;

    private static final String FILE_NAME = "nexus_wallpaper.jpg";

    private final MainActivity activity;
    private final LauncherSettings settings;
    private final Runnable onApplied;

    public WallpaperPickerDialog(MainActivity activity, LauncherSettings settings, Runnable onApplied) {
        this.activity = activity;
        this.settings = settings;
        this.onApplied = onApplied;
    }

    /** Opens the gallery picker; the chosen image is copied into the app storage. */
    public void pickFromGallery() {
        pickImage();
    }

    public void applySystem() {
        settings.setBackgroundMode(LauncherSettings.BG_SYSTEM);
        settings.setBackgroundPath(null);
        notifyChanged();
    }

    public void applyDefault() {
        settings.setBackgroundMode(LauncherSettings.BG_DEFAULT);
        settings.setBackgroundPath(null);
        deleteCustomWallpaper();
        notifyChanged();
    }

    @SuppressWarnings("deprecation")
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            activity.startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("image/*");
            try {
                activity.startActivityForResult(fallback, REQUEST_PICK_IMAGE);
            } catch (Exception second) {
                Toast.makeText(activity, "Nessuna galleria disponibile", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK_IMAGE) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        copyImage(data.getData());
        return true;
    }

    private void copyImage(Uri uri) {
        File target = new File(activity.getFilesDir(), FILE_NAME);
        InputStream input = null;
        OutputStream output = null;
        try {
            input = activity.getContentResolver().openInputStream(uri);
            if (input == null) {
                Toast.makeText(activity, "Impossibile leggere l'immagine", Toast.LENGTH_SHORT).show();
                return;
            }
            output = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();

            settings.setBackgroundPath(target.getAbsolutePath());
            settings.setBackgroundMode(LauncherSettings.BG_IMAGE);
            notifyChanged();
        } catch (Exception e) {
            Toast.makeText(activity, "Impossibile usare questa immagine", Toast.LENGTH_SHORT).show();
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private void deleteCustomWallpaper() {
        try {
            File file = new File(activity.getFilesDir(), FILE_NAME);
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        } catch (Exception ignored) {
            // nothing to do
        }
    }

    private void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // nothing to do
        }
    }

    private void notifyChanged() {
        if (onApplied != null) {
            onApplied.run();
        }
    }
}
