package gal.uvigo.burnout_app.helpers;

public final class AppPackageFilter {

    private AppPackageFilter() {
    }

    public static boolean isNoisePackage(String pkg) {
        if (pkg == null) return true;

        if (pkg.contains("launcher")) return true;
        if (pkg.contains("quickstep")) return true;

        if (pkg.equals("com.android.systemui")) return true;
        if (pkg.contains("permissioncontroller")) return true;
        if (pkg.equals("com.android.")) return true;
        if (pkg.startsWith("com.google.android.gms")) return true;

        if (pkg.contains("dynamite")) return true;

        if (pkg.contains("googlequicksearchbox")) return true;
        if (pkg.contains("tachyon")) return true;

        if (pkg.startsWith("com.sec.android.app.launcher")) return true;

        return false;
    }


}