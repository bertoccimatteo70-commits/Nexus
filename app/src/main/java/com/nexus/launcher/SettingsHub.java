package com.nexus.launcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Nexus UI settings: a dashboard of large cards with dedicated, animated sub pages.
 * Every row is wired to {@link LauncherSettings} and to the live launcher, so changes are
 * applied immediately and persist across restarts.
 */
public class SettingsHub extends FrameLayout {

    private static final int PAGE_DASHBOARD = -1;

    private static final int CAT_PERSONALIZATION = 0;
    private static final int CAT_HOME = 1;
    private static final int CAT_DISPLAY = 2;
    private static final int CAT_ANIMATIONS = 3;
    private static final int CAT_APPS = 4;
    private static final int CAT_WIDGETS = 5;
    private static final int CAT_PERFORMANCE = 6;
    private static final int CAT_PRIVACY = 7;
    private static final int CAT_SYSTEM = 8;
    private static final int CAT_ABOUT = 9;
    private static final int CAT_DEVICE = 10;
    private static final int CAT_COMPARE = 11;

    private static final String[] CATEGORY_TITLES = {
            "Personalizzazione", "Schermata Home", "Schermo e luminosità", "Animazioni",
            "Applicazioni", "Widget", "Prestazioni", "Privacy e sicurezza", "Sistema",
            "Informazioni su Nexus", "Informazioni sul dispositivo", "Confronto versioni"
    };

    private static final String[] CATEGORY_DESCRIPTIONS = {
            "Tema, colore accento, icone, sfondo e anteprima",
            "Griglia, etichette, pagine, dock e cartelle",
            "Contrasto, scala del testo e opzioni del display",
            "Transizioni, frequenza 60/120 Hz ed effetto cambio pagina",
            "Cassetto app, ricerca e visibilità delle applicazioni",
            "Aggiungi, ridimensiona e disponi i widget sulla home",
            "Nexus Lite: leggero, essenziale e senza fronzoli",
            "Dove restano i dati e quali permessi usa il launcher",
            "Backup locale, ripristini, versione e build",
            "Licenza, crediti, supporto e repository",
            "Hardware, memoria, batteria e dati di sistema",
            "Scopri cosa cambia tra Nexus e Nexus Lite."
    };

    private static final int[] CATEGORY_ICONS = {
            R.drawable.ic_set_palette, R.drawable.ic_set_home, R.drawable.ic_set_display,
            R.drawable.ic_set_animation, R.drawable.ic_apps, R.drawable.ic_set_widget,
            R.drawable.ic_set_performance, R.drawable.ic_set_privacy,
            R.drawable.ic_set_system, R.drawable.ic_set_info, R.drawable.ic_set_device,
            R.drawable.ic_set_compare
    };

    private static final String[] CATEGORY_KEYWORDS = {
            "tema scuro chiaro notte giorno accento colore icone forma dimensione sfondo wallpaper etichette",
            "griglia colonne pagine dock cartelle layout ripristina etichette",
            "contrasto testo scala luminosita display schermo",
            "animazioni velocita transizioni pagine dissolvenza",
            "cassetto app ricerca ordine alfabetico nascoste visibilita",
            "widget aggiungi rimuovi ridimensiona sposta",
            "prestazioni nexus lite modalita memoria batteria",
            "privacy sicurezza dati permessi policy legale",
            "backup ripristina reset versione build launcher predefinito",
            "informazioni licenza gpl crediti supporto github copyright",
            "dispositivo hardware batteria processore ram memoria schermo densita frequenza "
                    + "aggiornamento temperatura modello produttore android build",
            "confronto versioni differenze lite nexus tabella funzioni disponibili"
    };

    /** Deep links used by the search bar on the dashboard. */
    private static final String[][] QUICK_LINKS = {
            {"Tema del launcher", "Chiaro, scuro o automatico", "0", "tema scuro chiaro automatico notte"},
            {"Colore accento", "Scegli il colore principale di Nexus", "0", "colore accento viola blu verde"},
            {"Forma e dimensione icone", "Arrotondata, cerchio o squadrata", "0", "icone forma dimensione"},
            {"Sfondo della home", "Galleria, sistema o predefinito", "0", "sfondo wallpaper immagine"},
            {"Colonne della griglia", "Quante app per riga", "1", "colonne griglia"},
            {"App nel dock", "Fino a cinque applicazioni", "1", "dock barra"},
            {"Etichette delle app", "Mostra o nascondi i nomi", "1", "etichette nomi"},
            {"Animazioni e velocità", "Durata delle transizioni", "3", "animazioni velocita"},
            {"App nascoste", "Scegli cosa mostrare nel cassetto", "4", "nascoste visibilita"},
            {"Nexus Lite", "Modalità leggera per dispositivi meno potenti", "6", "lite prestazioni"},
            {"Backup locale", "Esporta o importa la configurazione", "8", "backup esporta importa"},
            {"Licenza GPL v3", "Termini di utilizzo del codice", "9", "licenza gpl"},
            {"Privacy policy", "Come Nexus tratta i dati", "7", "privacy dati policy"},
            {"Informazioni sul dispositivo", "Hardware, batteria e build di Android", "10",
                    "dispositivo hardware batteria processore ram memoria schermo"},
            {"Confronto versioni", "Scopri cosa cambia tra Nexus e Nexus Lite", "11",
                    "confronto lite differenze tabella funzioni"}
    };

    private static final String REPO_URL = "https://github.com/bertoccimatteo70-commits/Nexus";
    private static final String ISSUES_URL = REPO_URL + "/issues";
    private static final String GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.txt";

    private final MainActivity activity;
    private final LauncherSettings settings;
    private final HomeScreen home;
    private final FrameLayout pageHost;
    private final List<PageEntry> stack = new ArrayList<>();

    private static final class PageEntry {
        final int id;
        View view;

        PageEntry(int id, View view) {
            this.id = id;
            this.view = view;
        }
    }

    public SettingsHub(MainActivity activity, HomeScreen home) {
        super(activity);
        this.activity = activity;
        this.home = home;
        this.settings = home.getSettings();

        setBackgroundColor(settings.pageBackgroundColor(activity));
        pageHost = new FrameLayout(activity);
        addView(pageHost, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        stack.add(new PageEntry(PAGE_DASHBOARD, createDashboard()));
        pageHost.addView(stack.get(0).view, matchParams());
    }

    private FrameLayout.LayoutParams matchParams() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }

    // -------------------------------------------------------------- navigation

    public int currentPageId() {
        return stack.isEmpty() ? PAGE_DASHBOARD : stack.get(stack.size() - 1).id;
    }

    /** Returns true when the hub consumed the back press, false when it should be closed. */
    public boolean handleBack() {
        if (stack.size() <= 1) {
            return false;
        }
        PageEntry top = stack.remove(stack.size() - 1);
        PageEntry previous = stack.get(stack.size() - 1);
        pageHost.addView(previous.view, matchParams());

        int duration = settings.animDuration(190);
        if (duration > 0) {
            top.view.animate()
                    .translationX(Ui.dp(activity, 56))
                    .alpha(0f)
                    .setDuration(duration)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            pageHost.removeView(top.view);
                        }
                    })
                    .start();
        } else {
            pageHost.removeView(top.view);
        }
        return true;
    }

    public void refreshPage() {
        if (stack.isEmpty()) {
            return;
        }
        PageEntry top = stack.get(stack.size() - 1);
        View fresh = createPage(top.id);
        pageHost.removeView(top.view);
        top.view = fresh;
        pageHost.addView(fresh, matchParams());
    }

    private void navigate(int category) {
        if (category < 0 || category >= CATEGORY_TITLES.length) {
            return;
        }
        View view = createPage(category);
        PageEntry entry = new PageEntry(category, view);
        if (!stack.isEmpty()) {
            pageHost.removeView(stack.get(stack.size() - 1).view);
        }
        stack.add(entry);
        pageHost.addView(view, matchParams());

        int duration = settings.animDuration(230);
        if (duration > 0) {
            view.setTranslationX(Ui.dp(activity, 44));
            view.setAlpha(0f);
            view.animate().translationX(0f).alpha(1f).setDuration(duration).start();
        }
    }

    private View createPage(int id) {
        switch (id) {
            case CAT_PERSONALIZATION:
                return buildPersonalization();
            case CAT_HOME:
                return buildHome();
            case CAT_DISPLAY:
                return buildDisplay();
            case CAT_ANIMATIONS:
                return buildAnimations();
            case CAT_APPS:
                return buildApplications();
            case CAT_WIDGETS:
                return buildWidgets();
            case CAT_PERFORMANCE:
                return buildPerformance();
            case CAT_PRIVACY:
                return buildPrivacy();
            case CAT_SYSTEM:
                return buildSystem();
            case CAT_ABOUT:
                return buildAbout();
            case CAT_DEVICE:
                return buildDeviceInfo();
            case CAT_COMPARE:
                return buildCompare();
            default:
                return createDashboard();
        }
    }

    /** Applies a change to the launcher and repaints the current page. */
    private void applyChange() {
        home.onSettingsChanged();
        refreshPage();
    }

    // --------------------------------------------------------------- dashboard

    private View createDashboard() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(settings.pageBackgroundColor(activity));
        root.setPadding(0, Ui.statusBarHeight(activity) + Ui.dp(activity, 10), 0, 0);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8),
                Ui.dp(activity, 20), 0);

        TextView title = text("Impostazioni", 30f, settings.textColor(activity), true);
        header.addView(title);

        TextView subtitle = text("Nexus UI · " + settings.launcherModeName(), 12.5f,
                settings.secondaryTextColor(activity), false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = Ui.dp(activity, 2);
        header.addView(subtitle, subtitleParams);
        root.addView(header);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12),
                Ui.dp(activity, 14), Ui.navBarHeight(activity) + Ui.dp(activity, 20));

        final EditText search = new EditText(activity);
        search.setHint("Cerca impostazioni");
        search.setHintTextColor(settings.secondaryTextColor(activity));
        search.setTextColor(settings.textColor(activity));
        search.setTextSize(14f);
        search.setSingleLine(true);
        search.setBackground(Ui.rounded(settings.searchBackground(activity), Ui.dp(activity, 18)));
        search.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 12),
                Ui.dp(activity, 18), Ui.dp(activity, 12));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        searchParams.bottomMargin = Ui.dp(activity, 14);
        content.addView(search, searchParams);

        final LinearLayout cards = new LinearLayout(activity);
        cards.setOrientation(LinearLayout.VERTICAL);
        content.addView(cards);
        renderDashboard(cards, "");

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderDashboard(cards, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void renderDashboard(LinearLayout container, String query) {
        container.removeAllViews();
        String trimmed = query == null ? "" : query.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            sectionLabel(container, "Categorie");
            for (int i = 0; i < CATEGORY_TITLES.length; i++) {
                if (availableInMode(i)) {
                    container.addView(categoryCard(i));
                }
            }
            TextView footer = text("Nexus " + BuildConfig.VERSION_NAME + " · "
                    + settings.launcherModeName(), 11.5f,
                    settings.secondaryTextColor(activity), false);
            footer.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            footerParams.topMargin = Ui.dp(activity, 8);
            container.addView(footer, footerParams);
            return;
        }

        int matches = 0;
        for (int i = 0; i < CATEGORY_TITLES.length; i++) {
            if (availableInMode(i) && categoryMatches(i, trimmed)) {
                container.addView(categoryCard(i));
                matches++;
            }
        }
        for (String[] link : QUICK_LINKS) {
            if (!matches(link[0] + " " + link[1] + " " + link[3], trimmed)) {
                continue;
            }
            final int category = Integer.parseInt(link[2]);
            LinearLayout row = simpleRow(link[0], link[1], "›");
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    navigate(category);
                }
            });
            container.addView(row);
            matches++;
        }
        if (matches == 0) {
            TextView empty = text("Nessuna impostazione trovata per \"" + query.trim() + "\"", 13f,
                    settings.secondaryTextColor(activity), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 28),
                    Ui.dp(activity, 8), Ui.dp(activity, 28));
            container.addView(empty);
        }
    }

    /** Nexus Lite hides the widget section: widgets are a Nexus only feature. */
    private boolean availableInMode(int index) {
        return !settings.isLite() || index != CAT_WIDGETS;
    }

    private boolean categoryMatches(int index, String query) {
        String haystack = CATEGORY_TITLES[index] + " " + CATEGORY_DESCRIPTIONS[index] + " "
                + CATEGORY_KEYWORDS[index];
        return matches(haystack, query);
    }

    private boolean matches(String haystack, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String lower = haystack.toLowerCase();
        for (String token : query.split("\\s+")) {
            if (!token.isEmpty() && !lower.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private View categoryCard(int index) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 14),
                Ui.dp(activity, 14), Ui.dp(activity, 14));
        card.setBackground(Ui.rounded(settings.cardColor(activity), Ui.dp(activity, 24),
                Ui.dp(activity, 1), settings.rowBackground(activity)));
        card.setClickable(true);

        int tint = LauncherSettings.CATEGORY_TINTS[index % LauncherSettings.CATEGORY_TINTS.length];
        FrameLayout iconBox = new FrameLayout(activity);
        iconBox.setBackground(Ui.rounded(tint, Ui.dp(activity, 16)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(CATEGORY_ICONS[index]);
        icon.setColorFilter(darken(tint));
        icon.setPadding(Ui.dp(activity, 11), Ui.dp(activity, 11),
                Ui.dp(activity, 11), Ui.dp(activity, 11));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                Ui.dp(activity, 48), Ui.dp(activity, 48));
        iconParams.rightMargin = Ui.dp(activity, 14);
        card.addView(iconBox, iconParams);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(CATEGORY_TITLES[index], 16f, settings.textColor(activity), true));
        TextView description = text(CATEGORY_DESCRIPTIONS[index], 12f,
                settings.secondaryTextColor(activity), false);
        description.setLineSpacing(Ui.dp(activity, 1), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = Ui.dp(activity, 3);
        texts.addView(description, descriptionParams);
        card.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = text("›", 22f, settings.secondaryTextColor(activity), false);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chevronParams.leftMargin = Ui.dp(activity, 8);
        card.addView(chevron, chevronParams);

        final int target = index;
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigate(target);
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 10);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout simpleRow(String title, String description, String trailing) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 13),
                Ui.dp(activity, 16), Ui.dp(activity, 13));
        row.setBackground(Ui.rounded(settings.cardColor(activity), Ui.dp(activity, 18)));
        row.setClickable(true);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 15f, settings.textColor(activity), false));
        if (!TextUtils.isEmpty(description)) {
            TextView sub = text(description, 12f, settings.secondaryTextColor(activity), false);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subParams.topMargin = Ui.dp(activity, 2);
            texts.addView(sub, subParams);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null) {
            TextView value = text(trailing, 14f, settings.secondaryTextColor(activity), false);
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            valueParams.leftMargin = Ui.dp(activity, 10);
            row.addView(value, valueParams);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 8);
        row.setLayoutParams(params);
        return row;
    }

    // ------------------------------------------------------------- page chrome

    private interface Body {
        void build(LinearLayout content);
    }

    private View buildPage(String title, String subtitle, Body body) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(settings.pageBackgroundColor(activity));
        root.setPadding(0, Ui.statusBarHeight(activity), 0, 0);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 8),
                Ui.dp(activity, 18), Ui.dp(activity, 8));

        TextView back = text("‹", 26f, settings.textColor(activity), false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.rounded(settings.rowBackground(activity), Ui.dp(activity, 18)));
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!handleBack()) {
                    home.closeSettings();
                }
            }
        });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                Ui.dp(activity, 40), Ui.dp(activity, 40));
        backParams.rightMargin = Ui.dp(activity, 12);
        header.addView(back, backParams);

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(title, 22f, settings.textColor(activity), true));
        TextView subtitleView = text(subtitle, 12f, settings.secondaryTextColor(activity), false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = Ui.dp(activity, 1);
        titles.addView(subtitleView, subtitleParams);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(header);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 6),
                Ui.dp(activity, 14), Ui.navBarHeight(activity) + Ui.dp(activity, 24));
        body.build(content);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void sectionLabel(LinearLayout parent, String label) {
        TextView view = text(label.toUpperCase(java.util.Locale.ITALIAN), 11f,
                settings.secondaryTextColor(activity), true);
        view.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = Ui.dp(activity, 6);
        params.topMargin = Ui.dp(activity, 12);
        params.bottomMargin = Ui.dp(activity, 8);
        parent.addView(view, params);
    }

    private void row(LinearLayout parent, String title, String subtitle, String value,
                     Runnable onClick) {
        LinearLayout row = simpleRow(title, subtitle, value);
        if (onClick != null) {
            row.addView(text("›", 20f, settings.secondaryTextColor(activity), false));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onClick.run();
                }
            });
        } else {
            row.setClickable(false);
        }
        parent.addView(row);
    }

    private void choiceRow(LinearLayout parent, String title, String subtitle,
                           final String[] labels, int selected, final SettingsSheet.OnSelect onSelect) {
        String value = selected >= 0 && selected < labels.length ? labels[selected] : "—";
        row(parent, title, subtitle, value, new Runnable() {
            @Override
            public void run() {
                SettingsSheet.choice(activity, settings, title, subtitle, labels,
                        Math.max(0, selected), onSelect);
            }
        });
    }

    private void switchRow(LinearLayout parent, final String title, String subtitle,
                           boolean checked, final CompoundButton.OnCheckedChangeListener listener) {
        final Switch toggle = new Switch(activity);
        toggle.setChecked(checked);
        toggle.setShowText(false);
        applySwitchColors(toggle, checked);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applySwitchColors(buttonView, isChecked);
                if (listener != null) {
                    listener.onCheckedChanged(buttonView, isChecked);
                }
            }
        });

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 12),
                Ui.dp(activity, 12), Ui.dp(activity, 12));
        row.setBackground(Ui.rounded(settings.cardColor(activity), Ui.dp(activity, 18)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 15f, settings.textColor(activity), false));
        if (!TextUtils.isEmpty(subtitle)) {
            TextView sub = text(subtitle, 12f, settings.secondaryTextColor(activity), false);
            sub.setLineSpacing(Ui.dp(activity, 1), 1f);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subParams.topMargin = Ui.dp(activity, 2);
            texts.addView(sub, subParams);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(toggle);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 8);
        parent.addView(row, params);
    }

    private void applySwitchColors(CompoundButton button, boolean checked) {
        if (!(button instanceof Switch)) {
            return;
        }
        int accent = settings.accentColor(activity);
        Switch toggle = (Switch) button;
        toggle.setThumbTintList(ColorStateList.valueOf(checked ? accent : 0xFFB6B6C0));
        toggle.setTrackTintList(ColorStateList.valueOf(
                checked ? (accent & 0x00FFFFFF) | 0x55000000 : 0x33000000));
    }

    private void bodyText(LinearLayout parent, String text) {
        TextView body = text(text, 13f, settings.secondaryTextColor(activity), false);
        body.setLineSpacing(Ui.dp(activity, 4), 1f);
        body.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 4),
                Ui.dp(activity, 6), Ui.dp(activity, 4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 8);
        parent.addView(body, params);
    }

    private void resetRow(LinearLayout parent, final String category, final Runnable reset) {
        sectionLabel(parent, "Ripristino");
        row(parent, "Ripristina " + category, "Riporta questa sezione ai valori predefiniti",
                null, new Runnable() {
                    @Override
                    public void run() {
                        SettingsSheet.confirm(activity, settings,
                                "Ripristinare " + category + "?",
                                "Le preferenze di questa sezione tornano ai valori predefiniti. "
                                        + "App, pagine e widget non vengono toccati.",
                                "Ripristina", new Runnable() {
                                    @Override
                                    public void run() {
                                        reset.run();
                                        applyChange();
                                    }
                                });
                    }
                });
    }

    // --------------------------------------------------------- personalization

    private View buildPersonalization() {
        return buildPage("Personalizzazione", "Aspetto, icone e sfondo di Nexus",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        content.addView(previewCard());

                        sectionLabel(content, "Tema");
                        themeRows(content);

                        if (!settings.isLite()) {
                            sectionLabel(content, "Colore accento");
                            content.addView(accentSwatches());
                        }

                        sectionLabel(content, "Icone");
                        if (!settings.isLite()) {
                            choiceRow(content, "Forma delle icone", "Arrotondata, cerchio o squadrata",
                                    new String[]{"Arrotondata", "Cerchio", "Squadrata"},
                                    settings.getIconShape(), new SettingsSheet.OnSelect() {
                                        @Override
                                        public void onSelect(int index) {
                                            settings.setIconShape(index);
                                            applyChange();
                                        }
                                    });
                        }
                        choiceRow(content, "Dimensione delle icone",
                                "Si applica a home, dock e cassetto",
                                new String[]{"Piccole (44 dp)", "Medie (52 dp)",
                                        "Grandi (60 dp)", "Molto grandi (68 dp)"},
                                iconSizeIndex(), new SettingsSheet.OnSelect() {
                                    @Override
                                    public void onSelect(int index) {
                                        int[] sizes = {44, 52, 60, 68};
                                        settings.setIconSizeDp(sizes[index]);
                                        applyChange();
                                    }
                                });
                        switchRow(content, "Etichette delle app",
                                "Mostra il nome sotto ogni icona", settings.showLabels(),
                                new CompoundButton.OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                                        settings.setShowLabels(checked);
                                        applyChange();
                                    }
                                });

                        if (!settings.isLite()) {
                            sectionLabel(content, "Sfondo");
                            row(content, "Sfondo della home", wallpaperLabel(), null,
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            showWallpaperSheet();
                                        }
                                    });
                        }

                        resetRow(content, "Personalizzazione", new Runnable() {
                            @Override
                            public void run() {
                                settings.resetPersonalization();
                            }
                        });
                    }
                });
    }

    private View previewCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 16),
                Ui.dp(activity, 16), Ui.dp(activity, 16));
        card.setBackground(Ui.rounded(settings.cardColor(activity), Ui.dp(activity, 24),
                Ui.dp(activity, 1), settings.rowBackground(activity)));

        int size = Ui.dp(activity, 54);
        ImageView icon = new ImageView(activity);
        try {
            Drawable appIcon = activity.getPackageManager()
                    .getApplicationIcon(activity.getPackageName());
            icon.setImageDrawable(MainActivity.roundIcon(appIcon, size, settings.getIconShape()));
        } catch (Exception ignored) {
            icon.setImageResource(R.drawable.ic_apps);
        }
        card.addView(icon, new LinearLayout.LayoutParams(size, size));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = Ui.dp(activity, 14);
        TextView title = text("Anteprima", 15f, settings.textColor(activity), true);
        texts.addView(title);
        String description = iconShapeName() + " · " + settings.getIconSizeDp() + " dp · "
                + (settings.showLabels() ? "etichette attive" : "senza etichette");
        TextView sub = text(description, 12f, settings.secondaryTextColor(activity), false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Ui.dp(activity, 3);
        texts.addView(sub, subParams);
        TextView hint = text("Le modifiche si applicano subito", 11f,
                accentColor(), false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = Ui.dp(activity, 4);
        texts.addView(hint, hintParams);
        card.addView(texts, textsParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(activity, 4);
        card.setLayoutParams(params);
        return card;
    }

    private int accentColor() {
        return settings.accentColor(activity);
    }

    private String iconShapeName() {
        String[] names = {"Arrotondata", "Cerchio", "Squadrata"};
        return names[Math.max(0, Math.min(2, settings.getIconShape()))];
    }

    private int iconSizeIndex() {
        int size = settings.getIconSizeDp();
        if (size <= 46) {
            return 0;
        }
        if (size <= 54) {
            return 1;
        }
        if (size <= 62) {
            return 2;
        }
        return 3;
    }

    private void themeRows(LinearLayout content) {
        final LauncherSettings.ThemeMode[] modes = {
                LauncherSettings.ThemeMode.LIGHT,
                LauncherSettings.ThemeMode.DARK,
                LauncherSettings.ThemeMode.SYSTEM
        };
        String[] labels = {"Chiaro", "Scuro", "Automatico (sistema)"};
        int selected = settings.getThemeMode() == LauncherSettings.ThemeMode.LIGHT ? 0
                : settings.getThemeMode() == LauncherSettings.ThemeMode.DARK ? 1 : 2;
        choiceRow(content, "Tema del launcher", "Chiaro, scuro o in base al sistema",
                labels, selected, new SettingsSheet.OnSelect() {
                    @Override
                    public void onSelect(int index) {
                        settings.setThemeMode(modes[index]);
                        applyChange();
                    }
                });
    }

    private View accentSwatches() {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 14),
                Ui.dp(activity, 14), Ui.dp(activity, 14));
        wrapper.setBackground(Ui.rounded(settings.cardColor(activity), Ui.dp(activity, 20)));

        LinearLayout swatches = new LinearLayout(activity);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        int current = settings.accentColor(activity);
        for (final int color : LauncherSettings.ACCENTS) {
            boolean selected = color == current;
            FrameLayout holder = new FrameLayout(activity);
            holder.setBackground(Ui.rounded(selected ? 0x22FFFFFF : 0x00000000,
                    Ui.dp(activity, 24), Ui.dp(activity, 2),
                    selected ? color : 0x00000000));
            View swatch = new View(activity);
            swatch.setBackground(Ui.rounded(color, Ui.dp(activity, 18)));
            FrameLayout.LayoutParams swatchParams = new FrameLayout.LayoutParams(
                    Ui.dp(activity, 36), Ui.dp(activity, 36), Gravity.CENTER);
            holder.addView(swatch, swatchParams);
            holder.setClickable(true);
            holder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settings.setAccentColor(color);
                    applyChange();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    Ui.dp(activity, 48), 1f);
            swatches.addView(holder, params);
        }
        wrapper.addView(swatches);
        TextView caption = text("Colore attuale applicato a selezioni, cursori e accenti",
                11.5f, settings.secondaryTextColor(activity), false);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = Ui.dp(activity, 6);
        wrapper.addView(caption, captionParams);
        return wrapper;
    }

    private String wallpaperLabel() {
        switch (settings.getBackgroundMode()) {
            case LauncherSettings.BG_IMAGE:
                return "Immagine personale";
            case LauncherSettings.BG_SYSTEM:
                return "Sfondo di sistema";
            default:
                return "Predefinito Nexus";
        }
    }

    private void showWallpaperSheet() {
        final SettingsSheet.Sheet sheet = SettingsSheet.create(activity, settings, "Sfondo della home",
                "Scegli un'immagine dalla galleria, lo sfondo di sistema o il gradiente Nexus.");
        String[] labels = {"Scegli dalla galleria", "Usa lo sfondo di sistema",
                "Sfondo predefinito Nexus"};
        for (int i = 0; i < labels.length; i++) {
            final int option = i;
            SettingsSheet.addChoiceRow(sheet.content, activity, settings, labels[i], null,
                    isWallpaperOptionSelected(i), false, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            sheet.dismiss();
                            if (option == 0) {
                                home.openWallpaperFromGallery();
                            } else if (option == 1) {
                                home.useSystemWallpaper();
                                applyChange();
                            } else {
                                home.useDefaultWallpaper();
                                applyChange();
                            }
                        }
                    });
        }
        SettingsSheet.addPrimaryButton(sheet.content, activity, settings, "Annulla", 0,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sheet.dismiss();
                    }
                });
        sheet.dialog.show();
    }

    private boolean isWallpaperOptionSelected(int option) {
        int mode = settings.getBackgroundMode();
        if (option == 0) {
            return mode == LauncherSettings.BG_IMAGE;
        }
        if (option == 1) {
            return mode == LauncherSettings.BG_SYSTEM;
        }
        return mode == LauncherSettings.BG_DEFAULT;
    }

    // ------------------------------------------------------------------- home

    private View buildHome() {
        return buildPage("Schermata Home", "Griglia, pagine, dock e cartelle",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        bodyText(content, homeSummary());

                        sectionLabel(content, "Griglia e icone");
                        if (!settings.isLite()) {
                            choiceRow(content, "Colonne della griglia", "Quante app per riga",
                                    new String[]{"Automatico", "4 colonne", "5 colonne", "6 colonne"},
                                    columnIndex(), new SettingsSheet.OnSelect() {
                                        @Override
                                        public void onSelect(int index) {
                                            int[] values = {0, 4, 5, 6};
                                            settings.setColumns(values[index]);
                                            applyChange();
                                        }
                                    });
                        }
                        choiceRow(content, "Dimensione delle icone", null,
                                new String[]{"Piccole (44 dp)", "Medie (52 dp)",
                                        "Grandi (60 dp)", "Molto grandi (68 dp)"},
                                iconSizeIndex(), new SettingsSheet.OnSelect() {
                                    @Override
                                    public void onSelect(int index) {
                                        int[] sizes = {44, 52, 60, 68};
                                        settings.setIconSizeDp(sizes[index]);
                                        applyChange();
                                    }
                                });
                        switchRow(content, "Etichette delle app",
                                "Mostra il nome sotto ogni icona", settings.showLabels(),
                                new CompoundButton.OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                                        settings.setShowLabels(checked);
                                        applyChange();
                                    }
                                });

                        if (!settings.isLite()) {
                            sectionLabel(content, "Pagine e dock");
                            row(content, "Gestisci pagine", home.getLayout().pageCount()
                                            + " pagine · pagina corrente "
                                            + (home.getPager().currentPage() + 1),
                                    null, new Runnable() {
                                        @Override
                                        public void run() {
                                            showPagesSheet();
                                        }
                                    });
                            row(content, "App nel dock", settings.getDockPackages().size()
                                            + " di " + DockBar.MAX_ITEMS + " app selezionate",
                                    null, new Runnable() {
                                        @Override
                                        public void run() {
                                            showDockSheet();
                                        }
                                    });
                            choiceRow(content, "Aspetto delle cartelle", "Sfondo dell'anteprima cartella",
                                    new String[]{"Vetro", "Solido", "Accento"},
                                    settings.getFolderStyle(), new SettingsSheet.OnSelect() {
                                        @Override
                                        public void onSelect(int index) {
                                            settings.setFolderStyle(index);
                                            applyChange();
                                        }
                                    });
                        }

                        sectionLabel(content, "Layout");
                        row(content, "Ripristina layout home",
                                "Ricrea le pagine iniziali con le app installate", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        SettingsSheet.confirm(activity, settings,
                                                "Ripristinare la home?",
                                                "Verranno ricreate le pagine iniziali. "
                                                        + "Le app installate non vengono toccate.",
                                                "Ripristina", new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        home.resetHomeLayout();
                                                        refreshPage();
                                                    }
                                                });
                                    }
                                });

                        resetRow(content, "Schermata Home", new Runnable() {
                            @Override
                            public void run() {
                                settings.resetHomeAppearance();
                            }
                        });
                    }
                });
    }

    private String homeSummary() {
        int apps = 0;
        HomeLayout layout = home.getLayout();
        for (int p = 0; p < layout.pageCount(); p++) {
            HomeLayout.Page page = layout.page(p);
            if (page != null) {
                apps += page.items.size();
            }
        }
        return layout.pageCount() + " pagine · " + apps + " elementi · griglia a "
                + settings.getColumns(activity) + " colonne";
    }

    private int columnIndex() {
        if (!settings.hasCustomColumns()) {
            return 0;
        }
        int columns = settings.getColumns(activity);
        if (columns <= 4) {
            return 1;
        }
        return columns == 5 ? 2 : 3;
    }

    private void showPagesSheet() {
        String[] actions = {"Aggiungi pagina a destra", "Aggiungi pagina a sinistra",
                "Rimuovi pagina corrente"};
        SettingsSheet.actions(activity, settings, "Pagine della home",
                "Ogni pagina può contenere app, cartelle e widget.", actions,
                new SettingsSheet.OnSelect() {
                    @Override
                    public void onSelect(int index) {
                        if (index == 0) {
                            int added = home.getLayout().addPage(false);
                            home.getPager().rebuild();
                            home.getPager().setCurrentPage(added, true);
                        } else if (index == 1) {
                            int added = home.getLayout().addPage(true);
                            home.getPager().rebuild();
                            home.getPager().setCurrentPage(added, true);
                        } else {
                            int current = home.getPager().currentPage();
                            HomeLayout.Page page = home.getLayout().page(current);
                            if (home.getLayout().pageCount() <= 1) {
                                toast("Serve almeno una pagina");
                            } else if (page != null && page.items.isEmpty()) {
                                home.getLayout().removePage(current);
                                home.getPager().setCurrentPage(Math.max(0, current - 1), false);
                                home.getPager().rebuild();
                            } else {
                                toast("La pagina contiene elementi: svuotala prima");
                            }
                        }
                        home.onSettingsChanged();
                        refreshPage();
                    }
                });
    }

    private void showDockSheet() {
        final List<AppInfo> apps = visibleInstalledApps();
        String[] labels = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        List<String> dock = settings.getDockPackages();
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).getLabel();
            checked[i] = dock.contains(apps.get(i).getPackageName());
        }
        SettingsSheet.multiChoice(activity, settings, "App nel dock",
                "Scegli fino a " + DockBar.MAX_ITEMS + " app. Le prime cinque selezioni vengono usate.",
                labels, checked, "Salva dock", new SettingsSheet.OnMultiSelect() {
                    @Override
                    public void onApply(boolean[] state) {
                        List<String> selected = new ArrayList<>();
                        for (int i = 0; i < state.length && selected.size() < DockBar.MAX_ITEMS; i++) {
                            if (state[i]) {
                                selected.add(apps.get(i).getPackageName());
                            }
                        }
                        if (selected.isEmpty()) {
                            settings.resetDock();
                        } else {
                            settings.setDockPackages(selected);
                        }
                        home.onSettingsChanged();
                        refreshPage();
                    }
                });
    }

    // ------------------------------------------------------------------ theme

    private View buildDisplay() {
        return buildPage("Schermo e luminosità", "Contrasto, scala del testo e display",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        bodyText(content, "Nexus controlla l'aspetto della propria interfaccia. "
                                + "La luminosità del pannello resta gestita da Android.");

                        sectionLabel(content, "Tema");
                        themeRows(content);
                        if (!settings.isLite()) {
                            choiceRow(content, "Contrasto dell'interfaccia",
                                    "Aumenta il distacco tra testi e superfici",
                                    new String[]{"Normale", "Alto"}, settings.getContrastMode(),
                                    new SettingsSheet.OnSelect() {
                                        @Override
                                        public void onSelect(int index) {
                                            settings.setContrastMode(index);
                                            applyChange();
                                        }
                                    });
                        }

                        sectionLabel(content, "Dimensioni");
                        choiceRow(content, "Scala del testo", "Dimensione delle etichette",
                                new String[]{"90%", "100%", "115%"}, labelScaleIndex(),
                                new SettingsSheet.OnSelect() {
                                    @Override
                                    public void onSelect(int index) {
                                        int[] values = {90, 100, 115};
                                        settings.setLabelScalePercent(values[index]);
                                        applyChange();
                                    }
                                });
                        choiceRow(content, "Scala delle icone", null,
                                new String[]{"44 dp", "52 dp", "60 dp", "68 dp"},
                                iconSizeIndex(), new SettingsSheet.OnSelect() {
                                    @Override
                                    public void onSelect(int index) {
                                        int[] sizes = {44, 52, 60, 68};
                                        settings.setIconSizeDp(sizes[index]);
                                        applyChange();
                                    }
                                });

                        sectionLabel(content, "Display");
                        row(content, "Luminosità dello schermo",
                                "Controllata da Android: nessun launcher può modificarla", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        openSystemSettings(android.provider.Settings.ACTION_DISPLAY_SETTINGS,
                                                "Impostazioni display non disponibili");
                                    }
                                });
                        bodyText(content, "Le icone di stato e navigazione seguono automaticamente "
                                + "il tema chiaro o scuro scelto qui sopra.");
                    }
                });
    }

    private int labelScaleIndex() {
        int scale = settings.getLabelScalePercent();
        if (scale < 95) {
            return 0;
        }
        return scale > 105 ? 2 : 1;
    }

    // ------------------------------------------------------------- animations

    private View buildAnimations() {
        return buildPage("Animazioni", "Transizioni, velocità ed effetti",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        switchRow(content, "Animazioni",
                                "Disattivale per la massima reattività", settings.animationsEnabled(),
                                new CompoundButton.OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                                        settings.setAnimationsEnabled(checked);
                                        applyChange();
                                    }
                                });
                        choiceRow(content, "Frequenza delle animazioni",
                                "Automatico segue lo schermo; 60 e 120 Hz usano il modo più vicino",
                                new String[]{"Automatico", "Standard (60 Hz)", "Veloce (120 Hz)"},
                                settings.getAnimationMode(), new SettingsSheet.OnSelect() {
                                    @Override
                                    public void onSelect(int index) {
                                        settings.setAnimationMode(index);
                                        applyChange();
                                    }
                                });
                        bodyText(content, refreshRateSummary());
                        if (!settings.isLite()) {
                            choiceRow(content, "Effetto cambio pagina",
                                    "Transizione tra le pagine della home",
                                    new String[]{"Scorrimento", "Dissolvenza", "Nessuno"},
                                    settings.getPageEffect(), new SettingsSheet.OnSelect() {
                                        @Override
                                        public void onSelect(int index) {
                                            settings.setPageEffect(index);
                                            applyChange();
                                        }
                                    });
                        }
                        bodyText(content, "Le superfici (cassetto, impostazioni, finestre) usano la "
                                + "stessa durata: se disattivi le animazioni si aprono istantaneamente.");

                        resetRow(content, "Animazioni", new Runnable() {
                            @Override
                            public void run() {
                                settings.resetAnimations();
                            }
                        });
                    }
                });
    }

    /** Honest report of what the panel can do and what the current mode requests. */
    private String refreshRateSummary() {
        int max = Math.round(settings.displayMaxRefreshRate());
        int now = Math.round(settings.displayCurrentRefreshRate());
        StringBuilder text = new StringBuilder("Pannello: ")
                .append(max).append(" Hz massimi, adesso ").append(now).append(" Hz. ");
        if (settings.getAnimationMode() == LauncherSettings.ANIM_120 && max < 100) {
            text.append("Questo schermo non arriva a 120 Hz: Nexus usa il massimo disponibile ")
                    .append("e non dichiara una frequenza che il pannello non ha.");
        } else if (settings.getAnimationMode() == LauncherSettings.ANIM_AUTO) {
            text.append("Automatico segue il modo scelto da Android per lo schermo.");
        } else {
            text.append("Nexus richiede alla finestra il modo più vicino a ")
                    .append(Math.round(settings.effectiveAnimationHz())).append(" Hz.");
        }
        return text.toString();
    }

    // ----------------------------------------------------------- applications

    private View buildApplications() {
        return buildPage("Applicazioni", "Cassetto, ricerca e visibilità",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        sectionLabel(content, "Cassetto app");
                        switchRow(content, "Ordine alfabetico",
                                "Disattivalo per mantenere l'ordine di installazione",
                                settings.drawerAlphabetical(),
                                new CompoundButton.OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                                        settings.setDrawerAlphabetical(checked);
                                        home.onSettingsChanged();
                                        refreshPage();
                                    }
                                });
                        switchRow(content, "La ricerca apre il cassetto",
                                "Scrivendo nella home si apre il cassetto già filtrato",
                                settings.searchOpensDrawer(),
                                new CompoundButton.OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                                        settings.setSearchOpensDrawer(checked);
                                        refreshPage();
                                    }
                                });

                        sectionLabel(content, "Visibilità");
                        row(content, "App visibili", settings.getHiddenPackages().isEmpty()
                                        ? "Tutte le app installate sono visibili"
                                        : settings.getHiddenPackages().size() + " app nascoste",
                                null, new Runnable() {
                                    @Override
                                    public void run() {
                                        showVisibilitySheet();
                                    }
                                });
                        row(content, "Scorciatoie delle app",
                                "Gestite dal sistema: Nexus apre le app con il loro intent standard",
                                null, null);
                        bodyText(content, "Le app nascoste non compaiono nel cassetto e vengono "
                                + "rimosse dalle pagine della home. Restano installate e tornano "
                                + "disponibili quando le riattivi.");

                        resetRow(content, "Applicazioni", new Runnable() {
                            @Override
                            public void run() {
                                settings.resetApplicationPrefs();
                                home.refreshEverything();
                            }
                        });
                    }
                });
    }

    private List<AppInfo> visibleInstalledApps() {
        List<AppInfo> installed = AppInfo.load(activity);
        List<AppInfo> result = new ArrayList<>();
        for (AppInfo app : installed) {
            if (!settings.isHidden(app.getPackageName())) {
                result.add(app);
            }
        }
        return result;
    }

    private void showVisibilitySheet() {
        final List<AppInfo> installed = AppInfo.load(activity);
        String[] labels = new String[installed.size()];
        boolean[] checked = new boolean[installed.size()];
        for (int i = 0; i < installed.size(); i++) {
            labels[i] = installed.get(i).getLabel();
            checked[i] = !settings.isHidden(installed.get(i).getPackageName());
        }
        SettingsSheet.multiChoice(activity, settings, "App visibili",
                "Deseleziona le app da nascondere nel cassetto e nella home.",
                labels, checked, "Salva visibilità", new SettingsSheet.OnMultiSelect() {
                    @Override
                    public void onApply(boolean[] state) {
                        List<String> hidden = new ArrayList<>();
                        for (int i = 0; i < state.length; i++) {
                            if (!state[i]) {
                                hidden.add(installed.get(i).getPackageName());
                            }
                        }
                        if (hidden.size() == installed.size() && !installed.isEmpty()) {
                            toast("Mantieni almeno un'app visibile");
                            return;
                        }
                        settings.setHiddenPackages(hidden);
                        home.refreshEverything();
                        refreshPage();
                    }
                });
    }

    // ---------------------------------------------------------------- widgets

    private View buildWidgets() {
        return buildPage("Widget", "Aggiungi, ridimensiona e disponi i widget",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        sectionLabel(content, "Aggiungi");
                        row(content, "Aggiungi un widget",
                                "Scegli un widget tra quelli installati sul dispositivo", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        home.requestAddWidget();
                                        toast("Scegli il widget dalla lista di Android");
                                    }
                                });

                        sectionLabel(content, "Widget sulla home");
                        List<HomeItem> widgets = placedWidgets();
                        if (widgets.isEmpty()) {
                            bodyText(content, "Non ci sono widget sulla home. Aggiungine uno qui "
                                    + "sopra: apparirà sulla pagina corrente.");
                        } else {
                            for (final HomeItem widget : widgets) {
                                row(content, widget.getLabel(),
                                        "Pagina " + (widgetPage(widget) + 1) + " · "
                                                + widget.getSpanX() + " × " + widget.getSpanY(),
                                        null, new Runnable() {
                                            @Override
                                            public void run() {
                                                showWidgetActions(widget);
                                            }
                                        });
                            }
                            row(content, "Rimuovi tutti i widget",
                                    "Libera le celle occupate dai widget", null,
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            SettingsSheet.confirm(activity, settings,
                                                    "Rimuovere tutti i widget?",
                                                    "I widget verranno rimossi dalla home. "
                                                            + "Le app non vengono toccate.",
                                                    "Rimuovi", new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            home.removeAllWidgets();
                                                            refreshPage();
                                                        }
                                                    });
                                        }
                                    });
                        }

                        bodyText(content, "Tieni premuto un widget sulla home per lo stesso menu "
                                + "di ridimensionamento e rimozione.");
                    }
                });
    }

    private List<HomeItem> placedWidgets() {
        List<HomeItem> result = new ArrayList<>();
        HomeLayout layout = home.getLayout();
        for (int p = 0; p < layout.pageCount(); p++) {
            HomeLayout.Page page = layout.page(p);
            if (page == null) {
                continue;
            }
            for (HomeItem item : page.items) {
                if (item.isWidget()) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private int widgetPage(HomeItem widget) {
        HomeLayout layout = home.getLayout();
        for (int p = 0; p < layout.pageCount(); p++) {
            HomeLayout.Page page = layout.page(p);
            if (page != null && page.items.contains(widget)) {
                return p;
            }
        }
        return 0;
    }

    private void showWidgetActions(final HomeItem widget) {
        String[] actions = {"Ridimensiona", "Sposta su un'altra pagina", "Rimuovi"};
        SettingsSheet.actions(activity, settings, widget.getLabel(),
                "Pagina " + (widgetPage(widget) + 1) + " · " + widget.getSpanX() + " × "
                        + widget.getSpanY(), actions, new SettingsSheet.OnSelect() {
                    @Override
                    public void onSelect(int index) {
                        if (index == 0) {
                            showWidgetResize(widget);
                        } else if (index == 1) {
                            showWidgetMove(widget);
                        } else {
                            home.getWidgetHost().deleteWidget(widget.getAppWidgetId());
                            home.getLayout().removeItem(widget);
                            home.onSettingsChanged();
                            refreshPage();
                            toast("Widget rimosso");
                        }
                    }
                });
    }

    private void showWidgetResize(final HomeItem widget) {
        int columns = Math.max(3, settings.getColumns(activity));
        final List<String> labels = new ArrayList<>();
        for (int spanY = 1; spanY <= 3; spanY++) {
            for (int spanX = 1; spanX <= columns; spanX++) {
                labels.add(spanX + " × " + spanY);
            }
        }
        SettingsSheet.choice(activity, settings, "Dimensione del widget",
                "Celle occupate sulla griglia della home", labels.toArray(new String[0]),
                (widget.getSpanY() - 1) * columns + (widget.getSpanX() - 1),
                new SettingsSheet.OnSelect() {
                    @Override
                    public void onSelect(int index) {
                        int columns2 = Math.max(3, settings.getColumns(activity));
                        int spanY = index / columns2 + 1;
                        int spanX = index % columns2 + 1;
                        home.resizeWidget(widget, spanX, spanY);
                        refreshPage();
                    }
                });
    }

    private void showWidgetMove(final HomeItem widget) {
        final int pageCount = home.getLayout().pageCount();
        String[] labels = new String[pageCount];
        for (int i = 0; i < pageCount; i++) {
            labels[i] = "Pagina " + (i + 1);
        }
        SettingsSheet.choice(activity, settings, "Sposta widget", "Scegli la pagina di destinazione",
                labels, widgetPage(widget), new SettingsSheet.OnSelect() {
                    @Override
                    public void onSelect(int index) {
                        home.moveWidgetToPage(widget, index);
                        refreshPage();
                    }
                });
    }

    // ------------------------------------------------------------ performance

    private View buildPerformance() {
        return buildPage("Prestazioni", "Nexus e Nexus Lite nello stesso APK",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        sectionLabel(content, "Modalità attiva");
                        row(content, "Nexus", "Esperienza completa con animazioni e sfondi nitidi",
                                settings.isLite() ? null : "Attiva", new Runnable() {
                                    @Override
                                    public void run() {
                                        settings.setLauncherMode(LauncherSettings.LauncherMode.FULL);
                                        applyChange();
                                    }
                                });
                        row(content, "Nexus Lite",
                                "Una pagina essenziale, icone grandi, ricerca chiara e nessun fronzolo",
                                settings.isLite() ? "Attiva" : null, new Runnable() {
                                    @Override
                                    public void run() {
                                        settings.setLauncherMode(LauncherSettings.LauncherMode.LITE);
                                        applyChange();
                                    }
                                });

                        sectionLabel(content, "Differenze");
                        bodyText(content, "Nexus: più pagine, drag and drop, cartelle, widget, "
                                + "sfondo personale, colore accento, ombre e impostazioni complete.");
                        bodyText(content, "Nexus Lite: una home pulita con le scorciatoie che usi "
                                + "di più, cassetto con campo \"Cerca app\", icone grandi, sfondo "
                                + "predefinito a metà risoluzione e nessun widget. Lo swipe resta "
                                + "fluido con inerzia ed effetto elastico.");
                        bodyText(content, "Il confronto completo voce per voce è in \"Confronto "
                                + "versioni\". Puoi cambiare modalità quando vuoi: ogni modalità "
                                + "mantiene il proprio layout.");
                    }
                });
    }

    // ---------------------------------------------------------------- privacy

    private View buildPrivacy() {
        return buildPage("Privacy e sicurezza", "Dati locali, permessi e documenti",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        bodyText(content, "Nexus non raccoglie, non trasmette e non condivide dati. "
                                + "Non ci sono account, analytics o connessioni di rete.");

                        sectionLabel(content, "Dati");
                        row(content, "Dove restano i dati",
                                "Preferenze, layout e sfondo nella memoria privata dell'app", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showDataSheet();
                                    }
                                });
                        row(content, "Privacy policy", "Testo completo della politica", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showPrivacyPolicy();
                                    }
                                });

                        sectionLabel(content, "Permessi");
                        row(content, "Permessi utilizzati",
                                "Visibilità app, sfondo, avvio e ruolo HOME", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showPermissions();
                                    }
                                });
                        row(content, "Impostazioni permessi dell'app",
                                "Apri la schermata di sistema di Nexus", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        openAppSystemSettings();
                                    }
                                });

                        sectionLabel(content, "Legale");
                        row(content, "Documentazione legale", "Licenza e componenti di terze parti",
                                null, new Runnable() {
                                    @Override
                                    public void run() {
                                        navigate(CAT_ABOUT);
                                    }
                                });
                    }
                });
    }

    private void showDataSheet() {
        SettingsSheet.message(activity, settings, "Dati memorizzati", dataSummary());
    }

    private String dataSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append("Tutti i dati restano sul dispositivo:\n\n")
                .append("• Preferenze (tema, accento, icone, animazioni, visibilità) nelle ")
                .append("SharedPreferences private dell'app.\n")
                .append("• Layout della home, cartelle e widget in un JSON nelle stesse ")
                .append("preferenze.\n")
                .append("• Lo sfondo scelto viene copiato nella cartella privata dell'app.\n")
                .append("• I backup vengono scritti solo dove scegli tu, tramite il selettore ")
                .append("di file di Android.\n\n")
                .append("Nexus non ha accesso a Internet: nel manifest non esiste il permesso ")
                .append("INTERNET. Disinstallando l'app tutti questi dati vengono rimossi.");
        return builder.toString();
    }

    private void showPermissions() {
        SettingsSheet.message(activity, settings, "Permessi utilizzati",
                "QUERY_ALL_PACKAGES — necessario per elencare le app installate nella home e "
                        + "nel cassetto.\n\n"
                        + "SET_WALLPAPER — permette di applicare lo sfondo di sistema scelto.\n\n"
                        + "RECEIVE_BOOT_COMPLETED — riavvia l'host dei widget dopo un riavvio.\n\n"
                        + "Ruolo HOME — richiesto solo quando imposti Nexus come launcher "
                        + "predefinito; puoi revocarlo dalle impostazioni di Android. Nexus non "
                        + "richiede permessi di rete, posizione, contatti o memoria esterna.");
    }

    private void showPrivacyPolicy() {
        SettingsSheet.message(activity, settings, "Privacy policy",
                "Nexus è un launcher locale. Non invia dati a server propri o di terzi e non "
                        + "include SDK pubblicitari.\n\n"
                        + "Dati trattati: preferenze dell'interfaccia, layout della home, elenco "
                        + "delle app installate (letto dal sistema e mai trasmesso), sfondo scelto "
                        + "dall'utente.\n\n"
                        + "Conservazione: i dati restano nella memoria privata dell'app fino alla "
                        + "disinstallazione o a un ripristino dalle impostazioni di Nexus.\n\n"
                        + "Backup: esportati solo su richiesta dell'utente nella posizione scelta "
                        + "nel selettore di file. Nexus non crea copie automatiche in cloud.\n\n"
                        + "Contatti: " + ISSUES_URL);
    }

    private void openAppSystemSettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            toast("Impostazioni app non disponibili");
        }
    }

    // ----------------------------------------------------------------- system

    private View buildSystem() {
        return buildPage("Sistema", "Backup, ripristini e informazioni di build",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        sectionLabel(content, "Backup locale");
                        row(content, "Esporta backup",
                                "Salva preferenze e layout in un file JSON", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        SettingsBackup.export(activity, settings);
                                    }
                                });
                        row(content, "Importa backup",
                                "Ripristina preferenze e layout da un file JSON", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        SettingsSheet.confirm(activity, settings, "Importare un backup?",
                                                "La configurazione attuale viene sostituita da quella "
                                                        + "contenuta nel file scelto.",
                                                "Scegli file", new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        SettingsBackup.importBackup(activity, settings);
                                                    }
                                                });
                                    }
                                });

                        sectionLabel(content, "Ripristini");
                        row(content, "Ripristina impostazioni",
                                "Tema, icone, animazioni e visibilità tornano ai valori iniziali",
                                null, new Runnable() {
                                    @Override
                                    public void run() {
                                        SettingsSheet.confirm(activity, settings,
                                                "Ripristinare le impostazioni?",
                                                "Le preferenze tornano ai valori predefiniti. "
                                                        + "Pagine, app e widget restano dove sono.",
                                                "Ripristina", new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        settings.resetPersonalization();
                                                        settings.resetHomeAppearance();
                                                        settings.resetAnimations();
                                                        settings.resetApplicationPrefs();
                                                        settings.setLauncherMode(
                                                                LauncherSettings.LauncherMode.FULL);
                                                        settings.setBackgroundMode(
                                                                LauncherSettings.BG_DEFAULT);
                                                        home.refreshEverything();
                                                        refreshPage();
                                                    }
                                                });
                                    }
                                });
                        row(content, "Ripristina il launcher",
                                "Cancella impostazioni, pagine, cartelle e widget", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        SettingsSheet.confirm(activity, settings,
                                                "Ripristinare tutto Nexus?",
                                                "Impostazioni, layout della home, cartelle e widget "
                                                        + "verranno azzerati. Le app installate non "
                                                        + "vengono toccate.",
                                                "Ripristina tutto", new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        home.resetEverything();
                                                        home.closeSettings();
                                                    }
                                                });
                                    }
                                });

                        sectionLabel(content, "Launcher");
                        row(content, "Launcher predefinito",
                                activity.isDefaultLauncher()
                                        ? "Nexus è il launcher predefinito"
                                        : "Nexus non è impostato come launcher predefinito",
                                null, new Runnable() {
                                    @Override
                                    public void run() {
                                        if (activity.isDefaultLauncher()) {
                                            openSystemSettings(
                                                    android.provider.Settings.ACTION_HOME_SETTINGS,
                                                    "Impostazioni home non disponibili");
                                        } else {
                                            activity.requestDefaultLauncher();
                                        }
                                    }
                                });

                        sectionLabel(content, "Build");
                        row(content, "Versione", "versionName " + BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", null);
                        row(content, "Tipo di build", "Compilazione corrente",
                                BuildConfig.BUILD_TYPE, null);
                        row(content, "Android", "Versione del sistema",
                                Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")", null);
                        row(content, "Dispositivo", "Modello rilevato", Build.MODEL, null);
                        row(content, "Informazioni sul dispositivo",
                                "Hardware, memoria, batteria e schermo", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        navigate(CAT_DEVICE);
                                    }
                                });

                        row(content, "Informazioni su Nexus", "Licenza, crediti e supporto", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        navigate(CAT_ABOUT);
                                    }
                                });
                    }
                });
    }

    private void openSystemSettings(String action, String errorMessage) {
        try {
            activity.startActivity(new Intent(action));
        } catch (Exception e) {
            toast(errorMessage);
        }
    }

    // ------------------------------------------------------------- device info

    private View buildDeviceInfo() {
        return buildPage("Informazioni sul dispositivo", "Dati reali letti dal sistema",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        content.addView(deviceHero());
                        content.addView(deviceActions());
                        for (DeviceInfo.Section section : DeviceInfo.collect(activity)) {
                            sectionLabel(content, section.title);
                            content.addView(infoCard(section));
                        }
                        bodyText(content, "I valori arrivano dalle API pubbliche di Android e "
                                + "vengono riletti ogni volta che apri o aggiorni questa "
                                + "schermata. Le informazioni che il sistema non espone restano "
                                + "indicate come non disponibili: nessun dato è simulato.");
                    }
                });
    }

    private View deviceHero() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 18),
                Ui.dp(activity, 16), Ui.dp(activity, 18));
        card.setBackground(Ui.rounded(settings.cardColor(activity), Ui.dp(activity, 24),
                Ui.dp(activity, 1), settings.rowBackground(activity)));

        int tint = LauncherSettings.CATEGORY_TINTS[10];
        int size = Ui.dp(activity, 56);
        FrameLayout iconBox = new FrameLayout(activity);
        iconBox.setBackground(Ui.rounded(tint, Ui.dp(activity, 18)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_set_device);
        icon.setColorFilter(darken(tint));
        icon.setPadding(Ui.dp(activity, 13), Ui.dp(activity, 13),
                Ui.dp(activity, 13), Ui.dp(activity, 13));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        card.addView(iconBox, new LinearLayout.LayoutParams(size, size));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = Ui.dp(activity, 14);
        texts.addView(text(DeviceInfo.deviceName(), 18f, settings.textColor(activity), true));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Ui.dp(activity, 3);
        texts.addView(text(DeviceInfo.androidSummary(), 12.5f,
                settings.secondaryTextColor(activity), false), subParams);
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        modeParams.topMargin = Ui.dp(activity, 3);
        texts.addView(text("Nexus " + BuildConfig.VERSION_NAME + " · "
                + settings.launcherModeName(), 11.5f, accentColor(), false), modeParams);
        card.addView(texts, textsParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(activity, 4);
        card.setLayoutParams(params);
        return card;
    }

    private View deviceActions() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView refresh = actionPill("Aggiorna");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshPage();
                toast("Dati aggiornati");
            }
        });
        row.addView(refresh, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView copy = actionPill("Copia dettagli");
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = Ui.dp(activity, 8);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyDeviceInfo();
            }
        });
        row.addView(copy, copyParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(activity, 10);
        row.setLayoutParams(params);
        return row;
    }

    private TextView actionPill(String label) {
        TextView pill = text(label, 13f, accentColor(), true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 11),
                Ui.dp(activity, 10), Ui.dp(activity, 11));
        pill.setBackground(Ui.rounded((accentColor() & 0x00FFFFFF) | 0x22000000,
                Ui.dp(activity, 18)));
        pill.setClickable(true);
        return pill;
    }

    private View infoCard(DeviceInfo.Section section) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 14),
                Ui.dp(activity, 16), Ui.dp(activity, 8));
        card.setBackground(Ui.rounded(settings.cardColor(activity), Ui.dp(activity, 24),
                Ui.dp(activity, 1), settings.rowBackground(activity)));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconBox = new FrameLayout(activity);
        iconBox.setBackground(Ui.rounded(section.tint, Ui.dp(activity, 13)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(section.iconRes);
        icon.setColorFilter(darken(section.tint));
        icon.setPadding(Ui.dp(activity, 9), Ui.dp(activity, 9),
                Ui.dp(activity, 9), Ui.dp(activity, 9));
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        header.addView(iconBox, new LinearLayout.LayoutParams(
                Ui.dp(activity, 38), Ui.dp(activity, 38)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titlesParams.leftMargin = Ui.dp(activity, 12);
        titles.addView(text(section.title, 15.5f, settings.textColor(activity), true));
        TextView subtitle = text(section.subtitle, 11.5f,
                settings.secondaryTextColor(activity), false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = Ui.dp(activity, 2);
        titles.addView(subtitle, subtitleParams);
        header.addView(titles, titlesParams);
        card.addView(header);

        for (DeviceInfo.Row row : section.rows) {
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lineParams.topMargin = Ui.dp(activity, 11);

            TextView label = text(row.label, 12.5f,
                    settings.secondaryTextColor(activity), false);
            line.addView(label, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView value = text(row.value, 12.5f,
                    row.available ? settings.textColor(activity)
                            : settings.secondaryTextColor(activity), false);
            value.setGravity(Gravity.END);
            value.setMaxLines(3);
            value.setLineSpacing(Ui.dp(activity, 2), 1f);
            if (!row.available) {
                value.setTypeface(value.getTypeface(), Typeface.ITALIC);
            }
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f);
            valueParams.leftMargin = Ui.dp(activity, 14);
            line.addView(value, valueParams);
            card.addView(line, lineParams);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 8);
        card.setLayoutParams(params);
        return card;
    }

    private void copyDeviceInfo() {
        ClipboardManager clipboard = (ClipboardManager) activity
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            toast("Appunti non disponibili");
            return;
        }
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "Nexus · informazioni dispositivo", DeviceInfo.toPlainText(activity)));
            toast("Dettagli copiati negli appunti");
        } catch (Exception e) {
            toast("Impossibile copiare i dettagli");
        }
    }

    // ------------------------------------------------------------------ about

    private View buildAbout() {
        return buildPage("Informazioni su Nexus", "Versione, licenza e supporto",
                new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        content.addView(aboutHero());

                        sectionLabel(content, "Applicazione");
                        row(content, "Nome", "Launcher Android in Java", "Nexus", null);
                        row(content, "Versione", "versionName e versionCode",
                                BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", null);
                        row(content, "Sviluppatore", "Informazione non disponibile",
                                "Non specificato", null);
                        bodyText(content, "Il profilo dello sviluppatore non è indicato nel "
                                + "repository: il campo resta un segnaposto e non è stato inventato.");

                        sectionLabel(content, "Licenza");
                        row(content, "Licenza", "GNU General Public License v3.0",
                                "GPL-3.0-only", new Runnable() {
                                    @Override
                                    public void run() {
                                        showLicense();
                                    }
                                });
                        row(content, "Licenze di terze parti", "Componenti incluse nell'APK",
                                null, new Runnable() {
                                    @Override
                                    public void run() {
                                        showThirdParty();
                                    }
                                });

                        sectionLabel(content, "Supporto e privacy");
                        row(content, "Privacy policy", "Come Nexus tratta i dati", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showPrivacyPolicy();
                                    }
                                });
                        row(content, "Supporto", "Segnala un problema su GitHub", null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        openUrl(ISSUES_URL);
                                    }
                                });
                        row(content, "Repository GitHub", REPO_URL, null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        openUrl(REPO_URL);
                                    }
                                });
                        row(content, "Testo della licenza", "Apri il documento ufficiale GPL v3",
                                null, new Runnable() {
                                    @Override
                                    public void run() {
                                        openUrl(GPL_URL);
                                    }
                                });
                    }
                });
    }

    private View aboutHero() {
        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 18),
                Ui.dp(activity, 16), Ui.dp(activity, 18));
        hero.setBackground(Ui.rounded(settings.cardColor(activity), Ui.dp(activity, 24),
                Ui.dp(activity, 1), settings.rowBackground(activity)));

        int size = Ui.dp(activity, 60);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setBackground(Ui.rounded(0x00000000, Ui.dp(activity, 16)));
        hero.addView(icon, new LinearLayout.LayoutParams(size, size));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = Ui.dp(activity, 14);
        texts.addView(text("Nexus", 19f, settings.textColor(activity), true));
        texts.addView(text("Nexus UI · launcher Android in Java", 12f,
                settings.secondaryTextColor(activity), false));
        hero.addView(texts, textsParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(activity, 4);
        hero.setLayoutParams(params);
        return hero;
    }

    // --------------------------------------------------------------- comparison

    private View buildCompare() {
        return buildPage("Confronto versioni",
                "Scopri cosa cambia tra Nexus e Nexus Lite.", new Body() {
                    @Override
                    public void build(LinearLayout content) {
                        content.addView(compareLegend());
                        content.addView(compareHeader());

                        compareSection(content, "Home screen", new String[][]{
                                {"Home a più pagine", "Più pagine scorrevoli con pagina centrale", "—", "✓"},
                                {"Swipe con inerzia", "La pagina segue il dito e continua con l'inerzia", "✓", "✓"},
                                {"Effetto elastico", "Rimbalzo oltre la prima e l'ultima pagina", "✓", "✓"},
                                {"Dock fisso", "Barra in basso sempre visibile durante lo swipe", "✓", "✓"},
                                {"Drag and drop", "Sposta icone e scorciatoie tra le celle", "—", "✓"},
                                {"Cartelle", "Raggruppa più app in una cartella", "—", "✓"},
                                {"Scorciatoie modificabili", "Tieni premuto e scegli Rimuovi dalla home", "✓", "—"}
                        });

                        compareSection(content, "Personalizzazione", new String[][]{
                                {"Tema", "Chiaro, scuro o automatico", "✓", "✓"},
                                {"Colore accento", "Otto colori per selezioni e cursori", "—", "✓"},
                                {"Forma delle icone", "Arrotondata, cerchio o squadrata", "—", "✓"},
                                {"Dimensione delle icone", "Da 44 a 68 dp", "✓", "✓"},
                                {"Etichette delle app", "Mostra o nascondi i nomi", "✓", "✓"},
                                {"Sfondo personale", "Galleria o sfondo di sistema", "—", "✓"},
                                {"Pacchetti di icone", "Non supportati in questa versione", "—", "—"}
                        });

                        compareSection(content, "Widget", new String[][]{
                                {"Aggiungere widget", "Scegli un widget dal sistema", "—", "✓"},
                                {"Widget sulla home", "Ridimensiona, sposta o rimuovi", "—", "✓"}
                        });

                        compareSection(content, "Animazioni", new String[][]{
                                {"Transizioni tra pagine", "Scorrimento fluido della home", "✓", "✓"},
                                {"Frequenza 60/120 Hz", "Automatico, 60 Hz o 120 Hz", "✓", "✓"},
                                {"Animazioni decorative", "Cassetto e impostazioni con transizioni", "◐", "✓"},
                                {"Effetto cambio pagina", "Scorrimento, dissolvenza o nessuno", "—", "✓"}
                        });

                        compareSection(content, "Prestazioni", new String[][]{
                                {"Pagine caricate quando servono", "Binding automatico delle pagine visibili", "✓", "✓"},
                                {"Cache delle icone", "Icone arrotondate riusate tra le viste", "✓", "✓"},
                                {"Sfondo leggero", "Sfondo predefinito a metà risoluzione", "✓", "—"},
                                {"Animazioni ridotte", "Solo transizioni essenziali", "✓", "—"},
                                {"Rendering semplificato", "Nessuna ombra sulle etichette", "✓", "—"}
                        });

                        compareSection(content, "Cassetto e ricerca", new String[][]{
                                {"Tutte le app installate", "Elenco completo dal cassetto", "✓", "✓"},
                                {"Ricerca", "Campo \"Cerca app\" con filtro immediato", "✓", "✓"},
                                {"Ordine alfabetico", "In Lite sempre attivo", "✓", "✓"},
                                {"App nascoste", "Nascondi app senza disinstallarle", "—", "✓"},
                                {"Aggiungi alla home", "Tieni premuta un'app nel cassetto", "✓", "✓"}
                        });

                        compareSection(content, "Aspetto", new String[][]{
                                {"Ombre sulle etichette", "Testo leggibile su ogni sfondo", "—", "✓"},
                                {"Effetti blur", "Non presenti in questa versione", "—", "—"},
                                {"Anteprima dal vivo", "Card di anteprima in Personalizzazione", "—", "✓"},
                                {"Tema automatico", "Segue l'impostazione di Android", "✓", "✓"}
                        });

                        compareSection(content, "Display", new String[][]{
                                {"Contrasto alto", "Testi e superfici più distinti", "—", "✓"},
                                {"Scala del testo", "90%, 100% o 115%", "✓", "✓"},
                                {"Frequenza di aggiornamento", "Richiesta onesta del modo migliore disponibile", "✓", "✓"}
                        });

                        bodyText(content, "La tabella riflette l'implementazione attuale: "
                                + "✓ disponibile, ◐ disponibile con limiti, — non disponibile. "
                                + "Le voci non presenti in nessuna delle due modalità sono dichiarate "
                                + "come tali invece di essere simulate.");
                    }
                });
    }

    private View compareLegend() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12),
                Ui.dp(activity, 14), Ui.dp(activity, 12));
        card.setBackground(Ui.rounded(settings.cardColor(activity), Ui.dp(activity, 18),
                Ui.dp(activity, 1), settings.rowBackground(activity)));
        card.addView(text("Legenda", 13f, settings.textColor(activity), true));
        card.addView(legendLine("✓", "Disponibile"));
        card.addView(legendLine("◐", "Disponibile con limitazioni"));
        card.addView(legendLine("—", "Non disponibile"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 4);
        card.setLayoutParams(params);
        return card;
    }

    private View legendLine(String symbol, String label) {
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, Ui.dp(activity, 3), 0, 0);
        TextView glyph = text(symbol, 14f, compareSymbolColor(symbol), true);
        glyph.setGravity(Gravity.CENTER);
        line.addView(glyph, new LinearLayout.LayoutParams(
                Ui.dp(activity, 26), LinearLayout.LayoutParams.WRAP_CONTENT));
        line.addView(text(label, 12f, settings.secondaryTextColor(activity), false));
        return line;
    }

    private void compareSection(LinearLayout content, String title, String[][] rows) {
        sectionLabel(content, title);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(settings.cardColor(activity), Ui.dp(activity, 20),
                Ui.dp(activity, 1), settings.rowBackground(activity)));
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                View divider = new View(activity);
                divider.setBackgroundColor(settings.rowBackground(activity));
                card.addView(divider, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(activity, 0.7f))));
            }
            compareRow(card, rows[i][0], rows[i][1], rows[i][2], rows[i][3]);
        }
        content.addView(card);
    }

    /** Three column header aligned with the comparison rows. */
    private View compareHeader() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10),
                Ui.dp(activity, 8), Ui.dp(activity, 10));
        row.setBackground(Ui.rounded(settings.searchBackground(activity), Ui.dp(activity, 16)));

        row.addView(text("Funzione", 12f, settings.secondaryTextColor(activity), true),
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView lite = text("Nexus Lite", 10f, settings.textColor(activity), true);
        lite.setGravity(Gravity.CENTER);
        lite.setMaxLines(2);
        row.addView(lite, new LinearLayout.LayoutParams(
                Ui.dp(activity, 44), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView nexus = text("Nexus", 10f, settings.textColor(activity), true);
        nexus.setGravity(Gravity.CENTER);
        row.addView(nexus, new LinearLayout.LayoutParams(
                Ui.dp(activity, 40), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(activity, 10);
        row.setLayoutParams(params);
        return row;
    }

    private void compareRow(LinearLayout parent, String feature, String description,
                            String lite, String nexus) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10),
                Ui.dp(activity, 8), Ui.dp(activity, 10));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(feature, 13.5f, settings.textColor(activity), false));
        TextView sub = text(description, 11f, settings.secondaryTextColor(activity), false);
        sub.setLineSpacing(Ui.dp(activity, 1), 1f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Ui.dp(activity, 2);
        texts.addView(sub, subParams);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(compareCell(lite), new LinearLayout.LayoutParams(
                Ui.dp(activity, 44), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(compareCell(nexus), new LinearLayout.LayoutParams(
                Ui.dp(activity, 40), LinearLayout.LayoutParams.WRAP_CONTENT));
        parent.addView(row);
    }

    private View compareCell(String symbol) {
        TextView cell = text(symbol, 17f, compareSymbolColor(symbol), true);
        cell.setGravity(Gravity.CENTER);
        cell.setContentDescription(symbolLabel(symbol));
        return cell;
    }

    private String symbolLabel(String symbol) {
        if ("✓".equals(symbol)) {
            return "disponibile";
        }
        if ("◐".equals(symbol)) {
            return "disponibile con limitazioni";
        }
        return "non disponibile";
    }

    private int compareSymbolColor(String symbol) {
        if ("✓".equals(symbol)) {
            return settings.accentColor(activity);
        }
        if ("◐".equals(symbol)) {
            return 0xFFD08A00;
        }
        return settings.secondaryTextColor(activity);
    }

    private void showLicense() {
        SettingsSheet.message(activity, settings, "Licenza",
                "Nexus è distribuito con GNU General Public License v3.0 (GPL-3.0-only).\n\n"
                        + "Puoi usare, studiare, modificare e ridistribuire il codice secondo i "
                        + "termini della licenza, mantenendo la stessa licenza per le opere derivate.\n\n"
                        + "L'avviso di licenza è nel file LICENSE del repository; il testo completo "
                        + "è disponibile su " + GPL_URL + ".\n\n"
                        + "Questo programma è distribuito nella speranza che sia utile, ma SENZA "
                        + "ALCUNA GARANZIA.");
    }

    private void showThirdParty() {
        SettingsSheet.message(activity, settings, "Licenze di terze parti",
                "AndroidX AppCompat — Apache License 2.0\n\n"
                        + "Material Components for Android — Apache License 2.0\n\n"
                        + "AndroidX ConstraintLayout — Apache License 2.0\n\n"
                        + "Android SDK — termini del progetto Android Open Source Project.\n\n"
                        + "Le licenze complete sono disponibili nelle rispettive distribuzioni.");
    }

    private void openUrl(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("Nessuna app disponibile per aprire il link");
        }
    }

    // ------------------------------------------------------------------ utils

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(view.getTypeface(), Typeface.BOLD);
        }
        return view;
    }

    private static int darken(int color) {
        int r = (int) (((color >> 16) & 0xFF) * 0.42f);
        int g = (int) (((color >> 8) & 0xFF) * 0.42f);
        int b = (int) ((color & 0xFF) * 0.42f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
