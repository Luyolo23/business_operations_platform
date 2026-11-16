package com.capaciti.ops.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BiasCheckerTest {

    private final BiasChecker checker = new BiasChecker();

    @Test
    void zeroWidthCharactersCannotHideARiskyPromise() {
        // A zero-width space between "guaran" and "tee" used to slip past the rule.
        var r = checker.check("We guaran\u200Btee this will be fixed tomorrow.");
        assertEquals(RiskLevel.MEDIUM, r.level());
        assertTrue(r.flags().contains("ABSOLUTE_PROMISE"));
    }

    @Test
    void fullWidthCharactersAreNormalisedBeforeScreening() {
        var r = checker.check("We \uFF47\uFF55\uFF41\uFF52\uFF41\uFF4E\uFF54\uFF45\uFF45 this will be fixed.");
        assertEquals(RiskLevel.MEDIUM, r.level());
        assertTrue(r.flags().contains("ABSOLUTE_PROMISE"));
    }

    @Test
    void groupedCardNumbersAreHighRisk() {
        var r = checker.check("The card 4111 1111 1111 1111 was charged twice.");
        assertEquals(RiskLevel.HIGH, r.level());
        assertTrue(r.flags().contains("PII_GROUPED_NUMBER"));
    }

    @Test
    void nullOrBlankTextHasNoRisk() {
        assertEquals(RiskLevel.NONE, checker.check(null).level());
        assertEquals(RiskLevel.NONE, checker.check("   ").level());
    }

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
