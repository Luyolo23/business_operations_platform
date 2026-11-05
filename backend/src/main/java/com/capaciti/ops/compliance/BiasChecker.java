package com.capaciti.ops.compliance;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Week 6: rule-based risk & bias screening of generated responses. Pure function - easy to unit test.
 * Each rule has a flag name, a severity and an explanation. The overall level is the highest severity hit.
 */
@Component
public class BiasChecker {

    private record Rule(String flag, Pattern pattern, RiskLevel level, String explanation) {
        static Rule of(String flag, String regex, RiskLevel level, String explanation) {
            return new Rule(flag, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), level, explanation);
        }
    }

    private static final List<Rule> RULES = List.of(
            Rule.of("PII_LONG_NUMBER", "\\b\\d{13,16}\\b", RiskLevel.HIGH,
                    "Contains a 13-16 digit number (possible ID or card number)."),
            Rule.of("DISCRIMINATORY_LANGUAGE",
                    "\\b(old people|young people|foreigners?|those people|people like you|illegal immigrants?)\\b",
                    RiskLevel.HIGH, "Language that generalises about a group of people."),
            Rule.of("ABSOLUTE_PROMISE", "\\b(guarantee[ds]?|definitely will|we promise|I promise)\\b|100%",
                    RiskLevel.MEDIUM, "Makes an absolute commitment the team may not be able to keep."),
            Rule.of("LEGAL_CLAIM", "\\b(legally|lawsuit|sue|liable|liability|illegal)\\b",
                    RiskLevel.MEDIUM, "Contains legal wording that should be reviewed by a human."),
            Rule.of("BLAMING_TONE", "\\b(your fault|you should have|as i already said|obviously|clearly you)\\b",
                    RiskLevel.MEDIUM, "Tone may read as blaming or condescending."),
            Rule.of("GENDERED_LANGUAGE", "\\b(manpower|chairman|salesman|guys|girls|ladies and gentlemen|he/she)\\b",
                    RiskLevel.LOW, "Gendered wording; prefer neutral alternatives."),
            Rule.of("CONTACT_DETAIL", "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RiskLevel.LOW,
                    "Contains an email address; confirm it is appropriate to share."));

    public RiskCheckResult check(String text) {
        RiskLevel worst = RiskLevel.NONE;
        List<String> flags = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (Rule r : RULES) {
            if (r.pattern().matcher(text).find()) {
                flags.add(r.flag());
                notes.add(r.flag() + ": " + r.explanation());
                if (r.level().ordinal() > worst.ordinal()) worst = r.level();
            }
        }
        return new RiskCheckResult(worst, flags, notes.isEmpty() ? "No risk indicators found." : String.join(" ", notes));
    }
}
