package com.nexus.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Device information gathered exclusively from public Android APIs. Every value that the
 * platform does not expose is reported as unavailable instead of being invented.
 */
public final class DeviceInfo {

    /** One label/value pair shown inside a section card. */
    public static final class Row {
        public final String label;
        public final String value;
        public final boolean available;

        Row(String label, String value, boolean available) {
            this.label = label;
            this.value = value;
            this.available = available;
        }
    }

    /** A category of device information. */
    public static final class Section {
        public final String title;
        public final String subtitle;
        public final int iconRes;
        public final int tint;
        public final List<Row> rows = new ArrayList<>();

        Section(String title, String subtitle, int iconRes, int tint) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
            this.tint = tint;
        }

        Section row(String label, String value) {
            String clean = clean(value);
            rows.add(new Row(label, clean != null ? clean : "Non disponibile", clean != null));
            return this;
        }

        Section unavailable(String label, String reason) {
            rows.add(new Row(label, reason, false));
            return this;
        }
    }

    private static final String UNAVAILABLE = "Non disponibile";

    private DeviceInfo() {
    }

    public static List<Section> collect(Context context) {
        List<Section> sections = new ArrayList<>();
        sections.add(deviceSection());
        sections.add(hardwareSection(context));
        sections.add(batterySection(context));
        sections.add(nexusSection(context));
        return sections;
    }

    /** Commercial name when the platform exposes it, otherwise the model name. */
    public static String deviceName() {
        String marketing = marketingName();
        if (marketing != null) {
            return marketing;
        }
        String model = clean(Build.MODEL);
        return model != null ? model : "Android";
    }

    public static String androidSummary() {
        String release = clean(Build.VERSION.RELEASE);
        return "Android " + (release != null ? release : "?")
                + " · API " + Build.VERSION.SDK_INT;
    }

    // --------------------------------------------------------------- sections

    private static Section deviceSection() {
        Section section = new Section("Dispositivo", "Identità del dispositivo e versione di Android",
                R.drawable.ic_set_device, LauncherSettings.CATEGORY_TINTS[0]);
        section.row("Nome commerciale", marketingName());
        section.row("Produttore", Build.MANUFACTURER);
        section.row("Modello", Build.MODEL);
        section.row("Nome del dispositivo", Build.DEVICE);
        section.row("Brand", Build.BRAND);
        section.row("Versione Android", Build.VERSION.RELEASE);
        section.row("Livello API", String.valueOf(Build.VERSION.SDK_INT));
        section.row("Numero build Android", Build.DISPLAY);
        section.row("Patch di sicurezza", Build.VERSION.SECURITY_PATCH);
        return section;
    }

    private static Section hardwareSection(Context context) {
        Section section = new Section("Hardware", "Processore, memoria e schermo",
                R.drawable.ic_set_performance, LauncherSettings.CATEGORY_TINTS[5]);

        section.row("Processore (SoC)", socName());
        section.row("Architettura", Build.SUPPORTED_ABIS != null
                ? TextUtils.join(" · ", Build.SUPPORTED_ABIS) : null);
        section.row("Core della CPU", Runtime.getRuntime().availableProcessors() + " core");
        section.row("Hardware", Build.HARDWARE);

        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            try {
                manager.getMemoryInfo(memory);
                section.row("RAM totale", formatBytes(memory.totalMem));
                section.row("RAM disponibile", formatBytes(memory.availMem));
            } catch (Exception ignored) {
                section.unavailable("RAM", UNAVAILABLE);
            }
        } else {
            section.unavailable("RAM", UNAVAILABLE);
        }

        try {
            StatFs storage = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            section.row("Memoria interna totale", formatBytes(storage.getTotalBytes()));
            section.row("Memoria interna disponibile", formatBytes(storage.getAvailableBytes()));
        } catch (Exception ignored) {
            section.unavailable("Memoria interna", UNAVAILABLE);
        }

        DisplayMetrics metrics = realMetrics(context);
        if (metrics != null && metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            section.row("Risoluzione dello schermo",
                    metrics.widthPixels + " × " + metrics.heightPixels + " px");
            section.row("Densità dello schermo",
                    metrics.densityDpi + " dpi (" + String.format(Locale.ITALIAN, "%.2f", metrics.density) + "x)");
        } else {
            section.unavailable("Schermo", UNAVAILABLE);
        }

        Display display = display(context);
        try {
            if (display != null) {
                float rate = display.getRefreshRate();
                if (rate > 1f && rate < 240f) {
                    section.row("Frequenza di aggiornamento",
                            String.format(Locale.ITALIAN, "%.1f Hz", rate));
                } else {
                    section.unavailable("Frequenza di aggiornamento", UNAVAILABLE);
                }
            } else {
                section.unavailable("Frequenza di aggiornamento", UNAVAILABLE);
            }
        } catch (Exception ignored) {
            section.unavailable("Frequenza di aggiornamento", UNAVAILABLE);
        }

        section.unavailable("GPU", "Android non espone la GPU tramite API pubbliche affidabili");
        return section;
    }

    private static Section batterySection(Context context) {
        Section section = new Section("Batteria", "Carica e stato attuali",
                R.drawable.ic_dev_battery, LauncherSettings.CATEGORY_TINTS[4]);

        Intent status;
        try {
            status = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (Exception e) {
            status = null;
        }

        int level = status != null
                ? status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        int scale = status != null
                ? status.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
        if (level >= 0 && scale > 0) {
            section.row("Percentuale", Math.round(level * 100f / scale) + "%");
        } else {
            section.unavailable("Percentuale", UNAVAILABLE);
        }

        if (status != null) {
            int state = status.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);
            section.row("Stato di carica", batteryStatus(state));

            int plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            section.row("Alimentazione", powerSource(plugged));

            int temperature = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,
                    Integer.MIN_VALUE);
            if (temperature > 0) {
                section.row("Temperatura",
                        String.format(Locale.ITALIAN, "%.1f °C", temperature / 10f));
            } else {
                section.unavailable("Temperatura", UNAVAILABLE);
            }

            String technology = clean(status.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
            section.row("Tecnologia", technology);
        } else {
            section.unavailable("Stato di carica", UNAVAILABLE);
        }
        return section;
    }

    private static Section nexusSection(Context context) {
        Section section = new Section("Nexus", "Versione installata e modalità attiva",
                R.drawable.ic_set_info, LauncherSettings.CATEGORY_TINTS[7]);
        section.row("Versione di Nexus", BuildConfig.VERSION_NAME);
        section.row("Version code", String.valueOf(BuildConfig.VERSION_CODE));
        section.row("Modalità attiva", new LauncherSettings(context).launcherModeName());
        section.row("Tipo di build", BuildConfig.BUILD_TYPE);
        section.row("Build number", BuildConfig.VERSION_CODE + " · " + BuildConfig.BUILD_TYPE);
        section.row("Repository", "github.com/bertoccimatteo70-commits/Nexus");
        return section;
    }

    // ------------------------------------------------------------------ data

    private static String socName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String model = clean(Build.SOC_MODEL);
            String manufacturer = clean(Build.SOC_MANUFACTURER);
            if (model != null && manufacturer != null
                    && !manufacturer.equalsIgnoreCase("unknown")) {
                return model + " · " + manufacturer;
            }
            if (model != null) {
                return model;
            }
            if (manufacturer != null && !manufacturer.equalsIgnoreCase("unknown")) {
                return manufacturer;
            }
        }
        return null;
    }

    private static String marketingName() {
        try {
            java.lang.reflect.Field field = Build.class.getField("MARKETING_NAME");
            Object value = field.get(null);
            if (value instanceof String) {
                return clean((String) value);
            }
        } catch (Throwable ignored) {
            // not available on this Android version
        }
        return null;
    }

    private static Display display(Context context) {
        try {
            WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            return manager != null ? manager.getDefaultDisplay() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static DisplayMetrics realMetrics(Context context) {
        Display display = display(context);
        if (display != null) {
            try {
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                    return metrics;
                }
            } catch (Exception ignored) {
                // fall back to the window metrics below
            }
        }
        try {
            return context.getResources().getDisplayMetrics();
        } catch (Exception e) {
            return null;
        }
    }

    private static String batteryStatus(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "In carica";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "In scarica";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "Carica completa";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "Non in carica";
            default:
                return "Sconosciuto";
        }
    }

    private static String powerSource(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "Alimentatore AC";
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "Wireless";
            default:
                return "Batteria";
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return null;
        }
        double gib = bytes / (1024d * 1024d * 1024d);
        if (gib >= 1d) {
            return String.format(Locale.ITALIAN, "%.1f GB", gib);
        }
        double mib = bytes / (1024d * 1024d);
        return String.format(Locale.ITALIAN, "%.0f MB", mib);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("unknown")) {
            return null;
        }
        return trimmed;
    }

    /** Plain text copy of every value, used by the copy button. */
    public static String toPlainText(Context context) {
        StringBuilder builder = new StringBuilder();
        builder.append("Nexus ").append(BuildConfig.VERSION_NAME)
                .append(" — Informazioni sul dispositivo\n");
        builder.append(deviceName()).append(" · ").append(androidSummary()).append("\n");
        for (Section section : collect(context)) {
            builder.append("\n").append(section.title).append("\n");
            for (Row row : section.rows) {
                builder.append("• ").append(row.label).append(": ")
                        .append(row.value).append("\n");
            }
        }
        return builder.toString();
    }
}
