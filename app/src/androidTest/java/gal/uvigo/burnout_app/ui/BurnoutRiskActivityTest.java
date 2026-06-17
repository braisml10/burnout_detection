package gal.uvigo.burnout_app.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static androidx.test.espresso.matcher.RootMatchers.isDialog;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import gal.uvigo.burnout_app.R;

@RunWith(AndroidJUnit4.class)
public class BurnoutRiskActivityTest {

    @Rule
    public ActivityScenarioRule<BurnoutRiskActivity> activityRule =
            new ActivityScenarioRule<>(BurnoutRiskActivity.class);

    @Test
    public void burnoutRiskScreen_displaysTopElements() {
        onView(withId(R.id.btnBack)).check(matches(isDisplayed()));
        onView(withId(R.id.tvTitle)).check(matches(isDisplayed()));

        onView(withId(R.id.cardRiskScore)).check(matches(isDisplayed()));
        onView(withId(R.id.tvRiskScoreCard)).check(matches(isDisplayed()));
        onView(withId(R.id.ivInfoScore)).check(matches(isDisplayed()));

        onView(withId(R.id.cardCurrentRisk)).check(matches(isDisplayed()));
        onView(withId(R.id.tvRiskLevel)).check(matches(isDisplayed()));
        onView(withId(R.id.tvDriversTitle)).check(matches(isDisplayed()));
        onView(withId(R.id.tvDriver1)).check(matches(isDisplayed()));
        onView(withId(R.id.tvDriver2)).check(matches(isDisplayed()));
        onView(withId(R.id.tvDriver3)).check(matches(isDisplayed()));
    }

    @Test
    public void burnoutRiskScreen_displaysTrendSection() {
        onView(withId(R.id.tvTrendTitle))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.cardTrend))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.lineChartRiskTrend))
                .perform(scrollTo())
                .check(matches(isDisplayed()));
    }

    @Test
    public void burnoutRiskScreen_displaysDimensionCards() {
        onView(withId(R.id.tvDimensionTitle))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.cardDimensionFragmentation))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.cardDimensionNight))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.cardDimensionNotifications))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.cardDimensionScreen))
                .perform(scrollTo())
                .check(matches(isDisplayed()));

        onView(withId(R.id.cardDimensionTrend))
                .perform(scrollTo())
                .check(matches(isDisplayed()));
    }

    @Test
    public void infoButton_opensInfoDialog() {
        onView(withId(R.id.ivInfoScore))
                .check(matches(isDisplayed()))
                .perform(click());

        onView(withText(R.string.common_ok))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
                .perform(click());
    }

    @Test
    public void backButton_closesScreen() {
        onView(withId(R.id.btnBack)).perform(click());
    }
}