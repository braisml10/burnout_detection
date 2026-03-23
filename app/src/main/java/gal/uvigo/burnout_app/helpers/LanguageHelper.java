package gal.uvigo.burnout_app.helpers;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LanguageHelper {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_LANGUAGE = "selected_language";
    private static final String DEFAULT_LANGUAGE = "es";

    public static Context applyLanguage(Context context) {
        String langCode = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);

        return updateContext(context, langCode);
    }

    public static Context updateContext(Context context, String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);

        Configuration config =
                new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);

        return context.createConfigurationContext(config);
    }
}