package com.example.burnout_app.helpers;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.Locale;

public class AppCategoryResolver {

    public static final String SOCIAL = "SOCIAL";
    public static final String MESSAGING = "MESSAGING";
    public static final String ENTERTAINMENT = "ENTERTAINMENT";
    public static final String WORK = "WORK";
    public static final String OTHER = "OTHER";

    /**
     * Resuelve una categoría "macro" (SOCIAL / MESSAGING / ENTERTAINMENT / WORK / OTHER).
     *
     * Estrategia (robusta):
     *  1) Heurística por packageName (para separar mensajería y cubrir apps que vienen undefined).
     *  2) Si no se pudo, usa ApplicationInfo.category (cuando el sistema lo expone).
     *  3) Fallback -> OTHER.
     *
     * Nota: para que PackageManager vea apps externas en Android 11+, añade <queries> en el Manifest.
     */
    public static String resolveCategory(Context ctx, String packageName) {
        if (ctx == null || packageName == null || packageName.trim().isEmpty()) return OTHER;

        // 1) Heurística por packageName (lo más fiable para tus 4 buckets)
        String byPkg = mapByPackageName(packageName);
        if (!OTHER.equals(byPkg)) return byPkg;

        // 2) ApplicationInfo.category (best effort)
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            int c = ai.category;

            if (c == ApplicationInfo.CATEGORY_SOCIAL) {
                // si el sistema la marca como social pero es mensajería, lo corrige mapByPackageName arriba
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

            // CATEGORY_UNDEFINED u otras categorías -> OTHER
            return OTHER;

        } catch (PackageManager.NameNotFoundException e) {
            return OTHER;
        } catch (SecurityException e) {
            // Sin visibilidad de paquetes (falta <queries>) o ROM restrictiva
            return OTHER;
        } catch (Throwable t) {
            return OTHER;
        }
    }

    public static String resolveAppLabel(Context ctx, String packageName) {
        if (ctx == null || packageName == null) return packageName;

        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            String out = (label != null) ? label.toString() : null;

            if (out == null || out.trim().isEmpty()) return packageName;
            return out;

        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        } catch (SecurityException e) {
            return packageName;
        } catch (Throwable t) {
            return packageName;
        }
    }

    // ==========================
    // Heurísticas por paquete
    // ==========================

    private static String mapByPackageName(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);

        // Ignorar "ruido" / sistema -> OTHER
        if (isNoisePackage(p)) return OTHER;

        // Mensajería
        if (containsAny(p,
                "whatsapp",
                "telegram",
                "signal",
                "skype",
                "messenger",
                "line.android",
                "kik",
                "discord",          // si lo consideras chat (muchos lo usan así)
                "slack",            // a veces WORK, pero si prefieres WORK, muévelo abajo
                "wechat",
                "viber"
        )) {
            // Si prefieres Slack como WORK, quítalo de aquí y déjalo en WORK.
            if (p.contains("slack")) return WORK;
            return MESSAGING;
        }

        // Social
        if (containsAny(p,
                "instagram",
                "facebook",
                "fb.",              // algunos paquetes
                "twitter",
                "x.",               // ojo: algunos paquetes raros
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

        // Entretenimiento (streaming / música / vídeo / juegos)
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
                "supercell",        // clash royale/brawl stars etc.
                "riotgames"
        )) {
            return ENTERTAINMENT;
        }

        // Trabajo / Productividad
        if (containsAny(p,
                "microsoft",
                "office",
                "outlook",
                "teams",
                "onedrive",
                "word",
                "excel",
                "powerpoint",
                "google.android.gm",    // Gmail
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

    private static boolean isNoisePackage(String p) {
        // launcher/home (varía por fabricante)
        if (p.contains("launcher")) return true;
        if (p.contains("quickstep")) return true;

        // system ui / settings
        if (p.equals("com.android.systemui")) return true;
        if (p.startsWith("com.android.")) {
            // excepción: chrome etc. se manejan fuera si quieres, pero por defecto lo dejamos como OTHER
            // Puedes afinar aquí si quieres.
            return p.contains("permissioncontroller") || p.contains("settings");
        }

        // Samsung / OEM
        if (p.startsWith("com.sec.android.app.launcher")) return true;

        return false;
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
