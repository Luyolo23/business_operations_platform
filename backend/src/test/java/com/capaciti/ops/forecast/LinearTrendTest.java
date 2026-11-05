package com.capaciti.ops.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LinearTrendTest {

    @Test
    void fitsPerfectLine() {
        var t = LinearTrend.fit(new double[]{1, 3, 5, 7});
        assertEquals(2.0, t.slope(), 1e-9);
        assertEquals(1.0, t.intercept(), 1e-9);
        assertEquals(9.0, t.predict(4), 1e-9);
    }

    @Test
    void flatSeriesHasZeroSlope() {
        var t = LinearTrend.fit(new double[]{4, 4, 4});
        assertEquals(0.0, t.slope(), 1e-9);
        assertEquals(4.0, t.predict(10), 1e-9);
    }

    @Test
    void emptySeriesIsSafe() {
        assertEquals(0.0, LinearTrend.fit(new double[0]).predict(5), 1e-9);
    }
}
