package gal.uvigo.burnout_app.helpers;

import java.util.Locale;

public final class UiTextUtils {

    private UiTextUtils() {
    }

    public static String getInitials(String firstName, String lastName) {
        String firstInitial = firstName != null && !firstName.trim().isEmpty()
                ? firstName.trim().substring(0, 1).toUpperCase(Locale.getDefault())
                : "";

        String lastInitial = lastName != null && !lastName.trim().isEmpty()
                ? lastName.trim().substring(0, 1).toUpperCase(Locale.getDefault())
                : "";

        return firstInitial + lastInitial;
    }

}