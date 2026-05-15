package gal.uvigo.burnout_app.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
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
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void mainScreen_displaysTopElements() {
        onView(withId(R.id.avatarCard)).check(matches(isDisplayed()));
        onView(withId(R.id.tvTitle)).check(matches(isDisplayed()));
        onView(withId(R.id.tvDate)).check(matches(isDisplayed()));
        onView(withId(R.id.cardBurnoutRisk)).check(matches(isDisplayed()));
        onView(withId(R.id.cardScreenTime)).check(matches(isDisplayed()));
        onView(withId(R.id.cardNotifications)).check(matches(isDisplayed()));
    }

    @Test
    public void mainScreen_displaysLowerCardsAfterScroll() {
        onView(withId(R.id.cardMultitask))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.cardCommunication))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.cardChart))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.barChart3h))
                .perform(scrollTo())
                .check(matches(isDisplayed()));
    }

    @Test
    public void burnoutRiskCard_opensBurnoutRiskScreen() {
        onView(withId(R.id.cardBurnoutRisk)).perform(click());

        onView(withId(R.id.cardRiskScore)).check(matches(isDisplayed()));
        onView(withId(R.id.cardCurrentRisk)).check(matches(isDisplayed()));
    }

    @Test
    public void avatarCard_opensNavigationDrawer() {
        onView(withId(R.id.avatarCard)).perform(click());

        onView(withId(R.id.navView)).check(matches(isDisplayed()));
    }
}