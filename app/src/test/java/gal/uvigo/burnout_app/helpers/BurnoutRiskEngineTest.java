package gal.uvigo.burnout_app.helpers;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import gal.uvigo.burnout_app.data.entity.BurnoutRiskEntity;
import gal.uvigo.burnout_app.data.entity.DailyMetricsEntity;

public class BurnoutRiskEngineTest {

    private BurnoutRiskEngine engine;

    @Before
    public void setup() {
        engine = new BurnoutRiskEngine();
    }

    private DailyMetricsEntity createDay(
            long screenMs,
            int sessions,
            int switches,
            int notifications,
            long nightMs
    ) {
        return new DailyMetricsEntity(
                1,
                screenMs,
                0,
                screenMs,
                switches,
                5,
                sessions,
                notifications,
                nightMs
        );
    }

    @Test
    public void screenTime_low_withoutBaseline_returnsZero() {

        DailyMetricsEntity today =
                createDay(2L * 60 * 60 * 1000, 5, 5, 10, 0);

        BurnoutRiskEntity result =
                engine.evaluate(1, today, null, 10, 0.2);

        assertEquals(0.0, result.screenTimeScore, 0.01);
    }

    @Test
    public void screenTime_medium_withoutBaseline_returnsOne() {

        DailyMetricsEntity today =
                createDay(5L * 60 * 60 * 1000, 5, 5, 10, 0);

        BurnoutRiskEntity result =
                engine.evaluate(1, today, null, 10, 0.2);

        assertEquals(1.0, result.screenTimeScore, 0.01);
    }

    @Test
    public void screenTime_high_withoutBaseline_returnsTwo() {

        DailyMetricsEntity today =
                createDay(8L * 60 * 60 * 1000, 5, 5, 10, 0);

        BurnoutRiskEntity result =
                engine.evaluate(1, today, null, 10, 0.2);

        assertEquals(2.0, result.screenTimeScore, 0.01);
    }

    @Test
    public void nightUse_high_returnsTwo() {

        DailyMetricsEntity today =
                createDay(
                        4L * 60 * 60 * 1000,
                        5,
                        5,
                        10,
                        2L * 60 * 60 * 1000
                );

        BurnoutRiskEntity result =
                engine.evaluate(1, today, null, 10, 0.2);

        assertEquals(2.0, result.nightUseScore, 0.01);
    }

    @Test
    public void notificationPressure_high_returnsTwo() {

        DailyMetricsEntity today =
                createDay(4L * 60 * 60 * 1000, 5, 5, 150, 0);

        BurnoutRiskEntity result =
                engine.evaluate(1, today, null, 150, 1.0);

        assertEquals(2.0, result.notificationPressureScore, 0.01);
    }

    @Test
    public void withBaseline_trendDeviation_detected() {

        List<DailyMetricsEntity> baseline = new ArrayList<>();

        baseline.add(createDay(
                3L * 60 * 60 * 1000,
                5,
                5,
                20,
                0
        ));

        baseline.add(createDay(
                3L * 60 * 60 * 1000,
                5,
                5,
                20,
                0
        ));

        baseline.add(createDay(
                3L * 60 * 60 * 1000,
                5,
                5,
                20,
                0
        ));

        DailyMetricsEntity today =
                createDay(
                        8L * 60 * 60 * 1000,
                        30,
                        30,
                        150,
                        3L * 60 * 60 * 1000
                );

        BurnoutRiskEntity result =
                engine.evaluate(1, today, baseline, 150, 1.0);

        assertEquals(2.0, result.screenTimeScore, 0.01);
        assertEquals(2.0, result.trendDeviationScore, 0.01);
    }

    @Test
    public void negativeValues_areSafelyHandled() {

        DailyMetricsEntity today =
                createDay(-1, -5, -5, -10, -1);

        BurnoutRiskEntity result =
                engine.evaluate(1, today, null, -10, -1.0);

        assertEquals(0.0, result.screenTimeScore, 0.01);
        assertEquals(0.0, result.nightUseScore, 0.01);
    }
}