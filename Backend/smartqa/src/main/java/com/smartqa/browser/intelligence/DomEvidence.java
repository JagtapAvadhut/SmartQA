package com.smartqa.browser.intelligence;

import java.util.Locale;

/**
 * Deterministic evidence-quality scoring for extracted candidates.
 */
public final class DomEvidence {

    private DomEvidence() {
    }

    public static double quality(ElementCandidate el) {
        if (el == null) {
            return 0;
        }
        double q = 0;
        if (notBlank(el.accessibleName()) || notBlank(el.ariaLabel())) {
            q += 0.2;
        }
        if (notBlank(el.testId()) || notBlank(el.role())) {
            q += 0.15;
        }
        if (notBlank(el.headingContext()) || notBlank(el.ancestorContext())) {
            q += 0.2;
        }
        if (notBlank(el.parentContext()) || notBlank(el.siblingContext())) {
            q += 0.1;
        }
        if (notBlank(el.region())) {
            q += 0.1;
        }
        if (el.visible() && el.enabled()) {
            q += 0.15;
        }
        if (el.clickable() || el.hasAssociatedControl()) {
            q += 0.1;
        }
        return Math.min(1.0, Math.round(q * 100.0) / 100.0);
    }

    public static boolean ownsContext(ElementCandidate option, String ownerHint) {
        if (option == null || ownerHint == null || ownerHint.isBlank()) {
            return false;
        }
        String owner = ownerHint.toLowerCase(Locale.ROOT).trim();
        String blob = option.ownershipContext();
        return blob.contains(owner);
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
