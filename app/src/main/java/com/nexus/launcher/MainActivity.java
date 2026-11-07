package com.nexus.launcher;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * The Nexus home activity. It is registered for both the HOME and LAUNCHER categories so the
 * system can use it as the default launcher.
 */
public class MainActivity extends Activity {

    private LauncherSettings settings;
    private HomeScreen homeScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = new LauncherSettings(this);
        setupWindow();

        homeScreen = new HomeScreen(this, settings);
        setContentView(homeScreen);
        applySystemBars();

        if (!settings.isFirstLaunchDone()) {
            settings.setFirstLaunchDone(true);
            showWelcome();
        }
    }

    private void setupWindow() {
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setWindowAnimations(0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    }

    public void applySystemBars() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!settings.isDark(this)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (homeScreen != null) {
            homeScreen.goHome();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySystemBars();
        if (homeScreen != null) {
            homeScreen.onHostResume();
        }
    }

    @Override
    protected void onPause() {
        if (homeScreen != null) {
            homeScreen.onHostPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (homeScreen != null) {
            homeScreen.release();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (homeScreen != null && homeScreen.handleBack()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (homeScreen != null && homeScreen.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public LauncherSettings getSettings() {
        return settings;
    }

    public HomeScreen getHomeScreen() {
        return homeScreen;
    }

    public void launchApp(AppInfo app) {
        if (app == null) {
            return;
        }
        Intent intent = getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (intent == null) {
            Toast.makeText(this, "Impossibile aprire " + app.getLabel(), Toast.LENGTH_SHORT).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Impossibile aprire " + app.getLabel(), Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isDefaultLauncher() {
        RoleManager roleManager = getSystemService(RoleManager.class);
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return roleManager.isRoleHeld(RoleManager.ROLE_HOME);
        }
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null
                && getPackageName().equals(info.activityInfo.packageName);
    }

    public void requestDefaultLauncher() {
        RoleManager roleManager = getSystemService(RoleManager.class);
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            try {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), 9001);
                return;
            } catch (Exception ignored) {
                // fall through to the system home settings screen
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Impostazioni launcher non disponibili su questo dispositivo",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showWelcome() {
        SettingsSheet.choice(this, settings, "Benvenuto in Nexus",
                "Scegli come vuoi iniziare. Potrai cambiare modalità in qualsiasi momento dalle impostazioni.",
                new String[]{"Nexus — esperienza completa", "Nexus Lite — leggero e immediato"}, 0,
                new SettingsSheet.OnSelect() {
                    @Override
                    public void onSelect(int index) {
                        settings.setLauncherMode(index == 0
                                ? LauncherSettings.LauncherMode.FULL
                                : LauncherSettings.LauncherMode.LITE);
                        if (homeScreen != null) {
                            homeScreen.onSettingsChanged();
                        }
                        promptDefaultLauncher();
                    }
                });
    }

    public void promptDefaultLauncher() {
        if (isDefaultLauncher()) {
            return;
        }
        SettingsSheet.confirm(this, settings, "Impostare Nexus come launcher predefinito?",
                "Nexus deve essere il launcher predefinito per gestire home, cassetto e widget.",
                "Imposta", new Runnable() {
                    @Override
                    public void run() {
                        requestDefaultLauncher();
                    }
                });
    }

    /**
     * Crops an app icon into a rounded square, Pixel style.
     */
    public static Drawable roundIcon(Drawable source, int sizePx) {
        return roundIcon(source, sizePx, LauncherSettings.SHAPE_ROUNDED);
    }

    /**
     * Crops an app icon to the shape chosen in Personalizzazione.
     */
    public static Drawable roundIcon(Drawable source, int sizePx, int shape) {
        if (source == null || sizePx <= 0) {
            return source;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            if (shape == LauncherSettings.SHAPE_CIRCLE) {
                float radius = sizePx / 2f;
                canvas.drawCircle(radius, radius, radius, paint);
            } else {
                RectF rect = new RectF(0f, 0f, sizePx, sizePx);
                float radius = shape == LauncherSettings.SHAPE_SQUARE
                        ? sizePx * 0.115f
                        : sizePx * 0.235f;
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            source.setBounds(0, 0, sizePx, sizePx);
            source.draw(canvas);
            return new BitmapDrawable((android.content.res.Resources) null, bitmap);
        } catch (Throwable t) {
            return source;
        }
    }

    public int screenWidthPx() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return metrics.widthPixels;
    }
}
