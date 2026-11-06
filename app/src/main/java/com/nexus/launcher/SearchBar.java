package com.nexus.launcher;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Pill shaped search field used on the home screen and inside the app drawer.
 */
public class SearchBar extends LinearLayout {

    public interface OnQueryChangeListener {
        void onQueryChanged(String query);
    }

    private final LauncherSettings settings;
    private final EditText input;
    private final ImageView clearButton;
    private final ImageView searchIcon;

    private OnQueryChangeListener listener;

    public SearchBar(Context context, LauncherSettings settings) {
        super(context);
        this.settings = settings;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(context, 14), 0, Ui.dp(context, 6), 0);
        setClickable(true);

        searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setLayoutParams(new LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));
        addView(searchIcon);

        input = new EditText(context);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setTextSize(15f);
        input.setHint("Cerca app");
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0,
                LayoutParams.MATCH_PARENT, 1f);
        inputParams.leftMargin = Ui.dp(context, 10);
        input.setLayoutParams(inputParams);
        addView(input);

        clearButton = new ImageView(context);
        clearButton.setImageResource(R.drawable.ic_close);
        clearButton.setVisibility(View.GONE);
        clearButton.setPadding(Ui.dp(context, 8), Ui.dp(context, 8),
                Ui.dp(context, 8), Ui.dp(context, 8));
        clearButton.setLayoutParams(new LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
        clearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("");
            }
        });
        addView(clearButton);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s.length() > 0;
                clearButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
                if (listener != null) {
                    listener.onQueryChanged(s.toString());
                }
            }
        });

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                focusInput();
            }
        });

        applyTheme();
    }

    public void setOnQueryChangeListener(OnQueryChangeListener listener) {
        this.listener = listener;
    }

    public String getQuery() {
        return input.getText().toString();
    }

    public void setQuery(String query) {
        input.setText(query);
        if (query != null && !query.isEmpty()) {
            input.setSelection(query.length());
        }
    }

    public void focusInput() {
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    public void applyTheme() {
        setBackground(Ui.rounded(settings.searchBackground(getContext()),
                Ui.dp(getContext(), 24), Ui.dp(getContext(), 1),
                settings.rowBackground(getContext())));

        int textColor = settings.textColor(getContext());
        int secondary = settings.secondaryTextColor(getContext());
        input.setTextColor(textColor);
        input.setHintTextColor(secondary);
        searchIcon.setColorFilter(secondary, android.graphics.PorterDuff.Mode.SRC_IN);
        clearButton.setColorFilter(secondary, android.graphics.PorterDuff.Mode.SRC_IN);
    }
}
