package com.capaciti.ops.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BiasCheckerTest {

    private final BiasChecker checker = new BiasChecker();

    @Test
    void cleanTextHasNoRisk() {
        var r = checker.check("Hi Sam, thanks for reaching out. We are looking into your request.");
        assertEquals(RiskLevel.NONE, r.level());
        assertTrue(r.flags().isEmpty());
    }

    @Test
    void longNumberIsHighRisk() {
        var r = checker.check("Your ID 8001015009087 has been verified.");
        assertEquals(RiskLevel.HIGH, r.level());
        assertTrue(r.flags().contains("PII_LONG_NUMBER"));
    }

    @Test
    void absolutePromiseIsMediumRisk() {
        var r = checker.check("We guarantee this will be fixed tomorrow.");
        assertEquals(RiskLevel.MEDIUM, r.level());
    }

    @Test
    void genderedLanguageIsLowRisk() {
        var r = checker.check("Thanks guys, we are on it.");
        assertEquals(RiskLevel.LOW, r.level());
    }

    @Test
    void highestSeverityWins() {
        var r = checker.check("Thanks guys. We guarantee a fix for people like you.");
        assertEquals(RiskLevel.HIGH, r.level());
        assertEquals(3, r.flags().size());
    }
}
