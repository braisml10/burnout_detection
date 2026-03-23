package gal.uvigo.burnout_app.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import gal.uvigo.burnout_app.helpers.LanguageHelper;
import gal.uvigo.burnout_app.helpers.TimeKey;

public abstract class BaseActivity extends AppCompatActivity {

    protected TextView tvDayLabel;
    protected ImageButton btnPrevDay;
    protected ImageButton btnNextDay;

    protected int todayDay;
    protected int selectedDay;
    protected int minAllowedDay;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs =
                newBase.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String langCode = prefs.getString("selected_language", "es");
        super.attachBaseContext(LanguageHelper.updateContext(newBase, langCode));
    }

    protected void setupBackButton(@IdRes int backButtonId) {
        View backButton = findViewById(backButtonId);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    protected void initDaySelector(@IdRes int dayLabelId,
                                   @IdRes int prevButtonId,
                                   @IdRes int nextButtonId,
                                   int retentionDays) {
        tvDayLabel = findViewById(dayLabelId);
        btnPrevDay = findViewById(prevButtonId);
        btnNextDay = findViewById(nextButtonId);

        todayDay = TimeKey.todayEpochDay();
        selectedDay = todayDay;
        minAllowedDay = TimeKey.minAllowedEpochDay(retentionDays);

        if (btnPrevDay != null) {
            btnPrevDay.setOnClickListener(v -> {
                if (selectedDay > minAllowedDay) {
                    selectedDay--;
                    applyDayUi();
                    onDayChanged(selectedDay);
                }
            });
        }

        if (btnNextDay != null) {
            btnNextDay.setOnClickListener(v -> {
                if (selectedDay < todayDay) {
                    selectedDay++;
                    applyDayUi();
                    onDayChanged(selectedDay);
                }
            });
        }

        applyDayUi();
    }

    protected void applyDayUi() {
        if (tvDayLabel != null) {
            tvDayLabel.setText(TimeKey.dayLabel(this, selectedDay));
        }

        boolean canGoPrev = selectedDay > minAllowedDay;
        if (btnPrevDay != null) {
            btnPrevDay.setEnabled(canGoPrev);
            btnPrevDay.setAlpha(canGoPrev ? 1f : 0.35f);
        }

        boolean canGoNext = selectedDay < todayDay;
        if (btnNextDay != null) {
            btnNextDay.setEnabled(canGoNext);
            btnNextDay.setAlpha(canGoNext ? 1f : 0.35f);
        }
    }

    protected void onDayChanged(int selectedDay) {
        // Default vacío para Activities sin selector de día.
    }
}