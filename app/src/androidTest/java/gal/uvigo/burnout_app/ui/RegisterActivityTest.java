package gal.uvigo.burnout_app.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import gal.uvigo.burnout_app.R;

@RunWith(AndroidJUnit4.class)
public class RegisterActivityTest {

    @Rule
    public ActivityScenarioRule<RegisterActivity> activityRule =
            new ActivityScenarioRule<>(RegisterActivity.class);

    @Test
    public void registerScreen_displaysAllInputs() {
        onView(withId(R.id.btnBack)).check(matches(isDisplayed()));

        onView(withId(R.id.etNombre)).check(matches(isDisplayed()));
        onView(withId(R.id.etApellidos)).check(matches(isDisplayed()));
        onView(withId(R.id.etEmail)).check(matches(isDisplayed()));

        onView(withId(R.id.etPassword))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.etConfirmPassword))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.btnCreateAccount))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.tvGoLogin))
                .perform(scrollTo())
                .check(matches(isDisplayed()));
    }


    @Test
    public void registerScreen_allowsTypingUserData() {

        onView(withId(R.id.etNombre))
                .perform(typeText("Brais"), closeSoftKeyboard());

        onView(withId(R.id.etApellidos))
                .perform(typeText("Mondragon"), closeSoftKeyboard());

        onView(withId(R.id.etEmail))
                .perform(typeText("test@test.com"), closeSoftKeyboard());

        onView(withId(R.id.etPassword))
                .perform(typeText("password123"), closeSoftKeyboard());

        onView(withId(R.id.etConfirmPassword))
                .perform(typeText("password123"), closeSoftKeyboard());

        onView(withId(R.id.etNombre)).check(matches(isDisplayed()));
    }

    @Test
    public void goLoginButton_opensLoginScreen() {
        onView(withId(R.id.tvGoLogin)).perform(scrollTo(), click());

        onView(withId(R.id.etEmail)).check(matches(isDisplayed()));
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
    }

    @Test
    public void backButton_closesRegisterScreen() {

        onView(withId(R.id.btnBack)).perform(click());
    }

    @Test
    public void createAccountButton_withEmptyFields_doesNotCrash() {
        onView(withId(R.id.btnCreateAccount))
                .perform(scrollTo(), click());

        onView(withId(R.id.btnCreateAccount))
                .perform(scrollTo())
                .check(matches(isDisplayed()));
    }
}