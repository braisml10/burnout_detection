package gal.uvigo.burnout_app.helpers;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Locale;

public class AppCategoryResolver {

    public static final String SOCIAL = "SOCIAL";
    public static final String MESSAGING = "MESSAGING";
    public static final String ENTERTAINMENT = "ENTERTAINMENT";
    public static final String WORK = "WORK";
    public static final String OTHER = "OTHER";

    public static String resolveCategory(Context ctx, String packageName) {
        if (ctx == null || packageName == null || packageName.trim().isEmpty()) return OTHER;

        String byPkg = mapByPackageName(packageName);
        if (!OTHER.equals(byPkg)) return byPkg;

        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            int c = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c = ai.category;
            }

            if (c == ApplicationInfo.CATEGORY_SOCIAL) {
                return SOCIAL;
            }

            if (c == ApplicationInfo.CATEGORY_PRODUCTIVITY) {
                return WORK;
            }

            if (c == ApplicationInfo.CATEGORY_GAME
                    || c == ApplicationInfo.CATEGORY_VIDEO
                    || c == ApplicationInfo.CATEGORY_AUDIO
                    || c == ApplicationInfo.CATEGORY_IMAGE
                    || c == ApplicationInfo.CATEGORY_NEWS) {
                return ENTERTAINMENT;
            }

            return OTHER;

        } catch (PackageManager.NameNotFoundException e) {
            return OTHER;
        } catch (SecurityException e) {
            return OTHER;
        } catch (Throwable t) {
            return OTHER;
        }
    }

    public static String resolveAppLabel(Context ctx, String packageName) {
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);

            CharSequence label = pm.getApplicationLabel(ai);

            if (label != null) {
                String name = label.toString().trim();
                if (!name.isEmpty()) return name;
            }

        } catch (Exception e) {
        }

        return packageName;
    }

    private static String mapByPackageName(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);

        if (AppPackageFilter.isNoisePackage(p)) return OTHER;

        if (containsAny(p,
                "whatsapp",
                "telegram",
                "signal",
                "skype",
                "messenger",
                "line.android",
                "kik",
                "discord",
                "slack",
                "wechat",
                "viber"
        )) {
            if (p.contains("slack")) return WORK;
            return MESSAGING;
        }

        if (containsAny(p,
                "instagram",
                "facebook",
                "fb.",
                "twitter",
                "tiktok",
                "reddit",
                "snapchat",
                "pinterest",
                "linkedin",
                "threads",
                "mastodon"
        )) {
            return SOCIAL;
        }

        if (containsAny(p,
                "netflix",
                "primevideo",
                "hbomax",
                "hbo.",
                "disney",
                "disneyplus",
                "youtube",
                "youtubemusic",
                "spotify",
                "twitch",
                "soundcloud",
                "deezer",
                "steam",
                "epicgames",
                "supercell",
                "riotgames"
        )) {
            return ENTERTAINMENT;
        }

        if (containsAny(p,
                "microsoft",
                "office",
                "outlook",
                "teams",
                "onedrive",
                "word",
                "excel",
                "powerpoint",
                "com.google.android.gm",
                "com.google.android.apps.docs",
                "com.google.android.apps.sheets",
                "com.google.android.apps.meetings",
                "com.google.android.calendar",
                "notion",
                "evernote",
                "trello",
                "asana",
                "jira",
                "zoom",
                "webex"
        )) {
            return WORK;
        }

        return OTHER;
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null) return false;
        if (needles == null) return false;
        for (String n : needles) {
            if (n != null && !n.isEmpty() && haystack.contains(n)) return true;
        }
        return false;
    }
}
