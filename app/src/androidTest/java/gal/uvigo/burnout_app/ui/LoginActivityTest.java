package gal.uvigo.burnout_app.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import gal.uvigo.burnout_app.R;

@RunWith(AndroidJUnit4.class)
public class LoginActivityTest {

    @Rule
    public ActivityScenarioRule<LoginActivity> activityRule =
            new ActivityScenarioRule<>(LoginActivity.class);

    @Test
    public void loginScreen_displaysMainElements() {
        onView(withId(R.id.etEmail)).check(matches(isDisplayed()));
        onView(withId(R.id.etPassword)).check(matches(isDisplayed()));
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
        onView(withId(R.id.tvGoRegister)).check(matches(isDisplayed()));
        onView(withId(R.id.btnBack)).check(matches(isDisplayed()));
    }

    @Test
    public void loginScreen_allowsTypingCredentials() {
        onView(withId(R.id.etEmail))
                .perform(typeText("test@example.com"), closeSoftKeyboard());

        onView(withId(R.id.etPassword))
                .perform(typeText("password123"), closeSoftKeyboard());

        onView(withId(R.id.etEmail))
                .check(matches(isDisplayed()));

        onView(withId(R.id.etPassword))
                .check(matches(isDisplayed()));
    }

    @Test
    public void goRegisterButton_opensRegisterScreen() {
        onView(withId(R.id.tvGoRegister)).perform(click());

        onView(withId(R.id.etNombre)).check(matches(isDisplayed()));
        onView(withId(R.id.etApellidos)).check(matches(isDisplayed()));
        onView(withId(R.id.etConfirmPassword)).check(matches(isDisplayed()));
    }

    @Test
    public void backButton_closesLoginScreen() {
        onView(withId(R.id.btnBack)).perform(click());
    }
}