package com.smartqa.browser.intelligence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LocatorRanker {

    public record RankedElement(
            ElementCandidate element,
            double score,
            List<RankedLocator> locators,
            ScoreBreakdown breakdown
    ) {
        public RankedElement(ElementCandidate element, double score, List<RankedLocator> locators) {
            this(element, score, locators, null);
        }

        public String whySelected() {
            return breakdown == null ? "" : breakdown.whySelected();
        }
    }

    private LocatorRanker() {
    }

    public static List<RankedElement> rank(List<ElementCandidate> elements, String action, String target) {
        return rank(elements, action, target, null);
    }

    public static List<RankedElement> rank(List<ElementCandidate> elements, String action, String target, String location) {
        return rank(elements, action, target, location, 0);
    }

    /**
     * @param historyScore advisory only. Live graph and hard constraints still win.
     */
    public static List<RankedElement> rank(
            List<ElementCandidate> elements,
            String action,
            String target,
            String location,
            double historyScore
    ) {
        if (elements == null || elements.isEmpty() || target == null || target.isBlank()) {
            return List.of();
        }
        String hint = normalize(target);
        String ownerHint = extractOwnerHint(hint);
        List<RankedElement> ranked = new ArrayList<>();
        for (ElementCandidate element : elements) {
            HardConstraint hard = HardConstraintChecker.evaluate(element, action, ownerHint);
            if (hard != null) {
                continue;
            }
            ScoreBreakdown breakdown = scoreBreakdown(element, action, hint, location, historyScore);
            if (breakdown.total() <= 0) {
                continue;
            }
            ranked.add(new RankedElement(
                    element,
                    breakdown.total(),
                    LocatorSelectorBuilder.fromElement(element, action),
                    breakdown
            ));
        }
        ranked.sort(Comparator.comparingDouble(RankedElement::score).reversed());
        return preferRequestedInstance(ranked, target);
    }

    private static List<RankedElement> preferRequestedInstance(List<RankedElement> ranked, String target) {
        Integer requested = RepeatedInstanceDetector.requestedIndex(target);
        if (requested == null || ranked.size() < 2) {
            return ranked;
        }
        String signature = RepeatedInstanceDetector.signature(ranked.getFirst().element());
        List<RankedElement> same = new ArrayList<>();
        for (RankedElement rankedElement : ranked) {
            if (signature.equals(RepeatedInstanceDetector.signature(rankedElement.element()))) {
                same.add(rankedElement);
            }
        }
        if (same.size() < 2) {
            return ranked;
        }
        int index = requested < 0 ? same.size() : requested;
        if (index < 1 || index > same.size()) {
            return ranked;
        }
        RankedElement chosen = same.get(index - 1);
        ranked.remove(chosen);
        ranked.add(0, chosen);
        return ranked;
    }

    static double score(ElementCandidate element, String action, String hint) {
        return score(element, action, hint, null);
    }

    static double score(ElementCandidate element, String action, String hint, String location) {
        return scoreBreakdown(element, action, hint, location, 0).total();
    }

    static ScoreBreakdown scoreBreakdown(
            ElementCandidate element,
            String action,
            String hint,
            String location,
            double historyScore
    ) {
        String normalizedAction = action == null ? "" : action.toLowerCase(Locale.ROOT);
        double semanticScore = 0;
        Set<String> hintForms = synonymForms(hint);
        for (String field : element.semanticTokens()) {
            String value = normalize(field);
            if (value.isBlank()) {
                continue;
            }
            if (hintForms.contains(value) || value.equals(hint)) {
                semanticScore += 180;
            } else if (containsAny(value, hintForms) || hintForms.stream().anyMatch(value::contains) || hint.contains(value)) {
                semanticScore += 120;
            } else {
                semanticScore += tokenOverlap(hint, value) * 32;
                semanticScore += synonymTokenOverlap(hintForms, value) * 28;
            }
        }
        double accessibilityScore = 0;
        if (!element.testId().isBlank()) {
            accessibilityScore += 25;
        }
        String role = LocatorSelectorBuilder.inferRole(element, normalizedAction);
        double roleScore = 0;
        if ("click".equals(normalizedAction) && List.of("button", "link", "checkbox", "radio").contains(role)) {
            roleScore += 20;
        }
        if (("input".equals(normalizedAction) || "select".equals(normalizedAction) || "search".equals(normalizedAction))
                && List.of("textbox", "searchbox", "combobox").contains(role)) {
            roleScore += 25;
        }
        if ("verify".equals(normalizedAction) && "heading".equals(role)) {
            roleScore += 15;
        }
        if ("label".equals(element.tag().toLowerCase(Locale.ROOT))) {
            if ("select".equals(normalizedAction) || "input".equals(normalizedAction) || "search".equals(normalizedAction)) {
                roleScore -= 60;
            }
            if (element.hasAssociatedControl()) {
                roleScore += 10;
            }
        }
        String tag = element.tag() == null ? "" : element.tag().toLowerCase(Locale.ROOT);
        String inputType = element.inputType() == null ? "" : element.inputType().toLowerCase(Locale.ROOT);
        if ("click".equals(normalizedAction) && isSubmitLikeHint(hint)) {
            if ("button".equals(role) || "button".equals(tag) || "submit".equals(inputType)) {
                roleScore += 80;
            }
            if ("searchbox".equals(role) || "textbox".equals(role)) {
                roleScore -= 120;
            }
        }
        // Never prefer submit/search buttons for fill actions (same accessible name as the field).
        if ("input".equals(normalizedAction) || "search".equals(normalizedAction)) {
            if ("button".equals(role) || "button".equals(tag) || "submit".equals(inputType)) {
                roleScore -= 220;
            }
            if ("input".equals(tag) || "textarea".equals(tag)
                    || "searchbox".equals(role) || "textbox".equals(role) || "combobox".equals(role)) {
                roleScore += 80;
            }
            if (looksLikeLocationField(element) && !looksLikeLocationQuery(hint)) {
                roleScore -= 160;
            }
        }
        if ("select".equals(normalizedAction) && "select".equals(tag)) {
            roleScore += 40;
        }
        if ("select".equals(normalizedAction) && Set.of("combobox", "listbox").contains(role)) {
            roleScore += 35;
        }
        double contextScore = 0;
        String context = normalize(element.parentContext() + " " + element.nearbyText() + " " + element.label()
                + " " + element.headingContext() + " " + element.ancestorContext() + " " + element.siblingContext()
                + " " + element.region());
        if (context.contains("filter") || context.contains("facet") || context.contains("search")
                || "filter_panel".equals(normalize(element.region())) || "sidebar".equals(normalize(element.region()))) {
            contextScore += 40;
        }
        if ("search".equals(normalizedAction) && "search_area".equals(normalize(element.region()))) {
            contextScore += 50;
        }
        // Chrome words like "Filter" must not resolve to a long product/company title that merely contains the word.
        if ("click".equals(normalizedAction) && isChromeControlHint(hint)) {
            String blob = normalize(element.accessibleName() + " " + element.text() + " " + element.ariaLabel());
            if (!blob.equals(hint) && blob.contains(hint) && blob.length() > hint.length() + 10) {
                contextScore -= 150;
            }
            if (blob.contains(hint) && blob.length() > 40) {
                contextScore -= 80;
            }
            String region = normalize(element.region());
            if ("filter_panel".equals(region) || "sidebar".equals(region) || "header".equals(region)) {
                contextScore += 70;
            } else if ("content".equals(region) || "main".equals(region)) {
                contextScore -= 50;
            }
            if ("button".equals(role) || "button".equals(element.actionableRole()) || element.hasIcon()) {
                roleScore += 25;
            }
            int chromeArea = area(element.boundingBox());
            if (chromeArea > 0 && chromeArea < 8_000) {
                contextScore += 40;
            } else if (chromeArea >= 40_000) {
                contextScore -= 60;
            }
            String nearby = normalize(element.parentContext() + " " + element.nearbyText()
                    + " " + element.headingContext() + " " + element.siblingContext());
            if (nearby.contains("filter") || nearby.contains("facet") || nearby.contains("sort")
                    || nearby.contains("apply")) {
                contextScore += 20;
            }
        }
        // Ownership / "under Brand" style parent-child boost.
        String ownerHint = extractOwnerHint(hint);
        String optionHint = extractOptionHint(hint, ownerHint);
        if (!ownerHint.isBlank() && DomEvidence.ownsContext(element, ownerHint)) {
            contextScore += 70;
            if (optionMatches(element, optionHint)) {
                contextScore += 40;
            }
        } else if (!ownerHint.isBlank() && optionMatches(element, optionHint)
                && !DomEvidence.ownsContext(element, ownerHint)) {
            // Same option text outside the owning section — demote.
            contextScore -= 50;
        }
        // Sibling / nearby relational hints: "next to", "beside", "in section"
        if (hint.contains(" next to ") || hint.contains(" beside ") || hint.contains(" near ")
                || hint.contains(" in ")) {
            contextScore += relationalBoost(element, hint);
        }
        if ("checkbox".equals(role) || "radio".equals(role)) {
            roleScore += 15;
        }
        double actionabilityScore = 0;
        if (element.disabled() || !element.enabled()) {
            actionabilityScore -= 120;
        }
        if (element.readOnly() && ("input".equals(normalizedAction) || "select".equals(normalizedAction) || "search".equals(normalizedAction))) {
            actionabilityScore -= 80;
        }
        if (element.clickable() && "click".equals(normalizedAction)) {
            actionabilityScore += 10;
        }
        // Prefer compact exact-text clickable leaves over huge wrappers that merely contain the same text.
        if ("click".equals(normalizedAction)) {
            String exactText = normalize(element.text());
            if (!exactText.isBlank() && (hintForms.contains(exactText) || exactText.equals(hint)) && element.clickable()) {
                semanticScore += 40;
                int area = area(element.boundingBox());
                if (area > 0 && area < 40_000) {
                    actionabilityScore += 50;
                } else if (area >= 200_000) {
                    actionabilityScore -= 80;
                }
            }
        }
        double iconScore = iconIntentScore(element, normalizedAction, hint);
        double locationUnit = com.smartqa.intent.LocationHint.score(
                location, element.boundingBox(), element.inHeaderRegion());
        double locationScore = locationUnit * 80;
        double qualityScore = element.evidenceQuality() * 20;

        double visualScore = visualBoost(element);
        double frameScore = frameBoost(element);
        double shadowScore = shadowBoost(element);
        double filterActionBoost = filterActionBoost(element, normalizedAction, hint);
        double history = historyScore;
        double capabilityScore = 0;
        ControlType controlType = ControlClassifier.classifyFromCandidate(element);
        if (ActionCompatibility.isCompatible(action, controlType)) {
            capabilityScore = 25;
        }
        double controlTypeScore = "checkbox".equals(normalizedAction) && controlType == ControlType.CHECKBOX ? 40 : 0;
        if ("select".equals(normalizedAction) && controlType.supportsSelect()) {
            controlTypeScore = 40;
        }
        double containerScore = 0;
        if (!ownerHint.isBlank() && (DomEvidence.ownsContext(element, ownerHint)
                || containsIgnoreCase(element.containerId(), ownerHint))) {
            containerScore = 30;
        }
        double relationshipScore = 0;
        if (!element.parentId().isBlank() || !element.containerId().isBlank()) {
            relationshipScore = 12;
        }
        double optionValueScore = optionMatches(element, optionHint) ? 35 : 0;
        double entityScore = 0;
        if (!ownerHint.isBlank() && optionMatches(element, optionHint)) {
            entityScore = 20;
        } else if (!ownerHint.isBlank() && DomEvidence.ownsContext(element, ownerHint)) {
            entityScore = 10;
        }
        double rangeBoundScore = looksLikeRange(hint) && looksLikeRangeField(element) ? 20 : 0;
        double stateScore = element.checked() || element.selected() ? 8 : 0;
        double accessibleNameScore = element.accessibleName() != null && !element.accessibleName().isBlank() ? 15 : 0;
        double textExactScore = 0;
        double textSimilarityScore = 0;
        String exactText = normalize(element.text());
        if (!exactText.isBlank() && exactText.equals(hint)) {
            textExactScore = 50;
        } else if (!exactText.isBlank() && (exactText.contains(hint) || hint.contains(exactText))) {
            textSimilarityScore = 20;
        }
        double total = semanticScore
                + accessibilityScore
                + roleScore
                + contextScore
                + actionabilityScore
                + iconScore
                + locationScore
                + qualityScore
                + visualScore
                + frameScore
                + shadowScore
                + filterActionBoost
                + history
                + capabilityScore
                + controlTypeScore
                + containerScore
                + relationshipScore
                + optionValueScore
                + rangeBoundScore
                + stateScore
                + textExactScore
                + accessibleNameScore
                + treePathScore(element, ownerHint)
                + axNameScore(element, hint);

        String explanation = firstNonBlank(element.accessibleName(), element.text())
                + " matched '" + hint + "'"
                + (contextScore > 0 || containerScore > 0 ? ", owned/container relevant" : "")
                + (element.visible() ? ", visible" : "")
                + (element.clickable() ? ", actionable" : "")
                + (element.region() == null || element.region().isBlank() ? "" : ", region " + element.region())
                + (capabilityScore > 0 ? ", capability ok" : "")
                + ".";

        if (total >= 200) {
            String prefix = candidatePrefix(hint);
            com.smartqa.debug.TraceLogger.info("LOCATOR", prefix + "_CANDIDATE", "Ranked candidate",
                    com.smartqa.debug.TraceMeta.of(
                            "candidateId", element.candidateId(),
                            "target", hint,
                            "location", location == null || location.isBlank() ? "AUTO" : location,
                            "region", element.region(),
                            "headingContext", element.headingContext(),
                            "ancestorContext", truncate(element.ancestorContext(), 120),
                            "parentContext", truncate(element.parentContext(), 80),
                            "siblingContext", truncate(element.siblingContext(), 80),
                            "semanticScore", round2(semanticScore),
                            "accessibilityScore", round2(accessibilityScore),
                            "roleScore", round2(roleScore),
                            "locationScore", round2(locationUnit),
                            "actionabilityScore", round2(Math.max(0, actionabilityScore)),
                            "contextScore", round2(contextScore),
                            "iconScore", round2(iconScore),
                            "ownershipScore", round2(containerScore + Math.max(0, contextScore)),
                            "evidenceQuality", element.evidenceQuality(),
                            "finalScore", round2(total),
                            "explanation", explanation
                    ));
            com.smartqa.debug.TraceLogger.info("LOCATOR", "DOM_EVIDENCE_QUALITY", "Candidate evidence quality",
                    com.smartqa.debug.TraceMeta.of(
                            "candidateId", element.candidateId(),
                            "quality", element.evidenceQuality(),
                            "region", element.region()
                    ));
        }
        double ownership = 0;
        if (!ownerHint.isBlank() && DomEvidence.ownsContext(element, ownerHint)) {
            ownership = 70;
        }
        if ("FILTER_PANEL".equalsIgnoreCase(element.region()) || "SIDEBAR".equalsIgnoreCase(element.region())) {
            ownership += 20;
        }
        return new ScoreBreakdown(
                semanticScore,
                contextScore,
                roleScore,
                ownership,
                element.visible() ? 40 : 0,
                actionabilityScore,
                locationScore,
                accessibilityScore + accessibleNameScore,
                history,
                visualScore,
                frameScore,
                shadowScore,
                0,
                total,
                explanation.trim(),
                textExactScore,
                textSimilarityScore,
                accessibleNameScore,
                capabilityScore,
                controlTypeScore,
                containerScore,
                entityScore,
                optionValueScore,
                rangeBoundScore,
                locationScore,
                stateScore,
                relationshipScore,
                null
        );
    }

    /** rank with explicit owner context (filter field name). */
    public static List<RankedElement> rankOwned(
            List<ElementCandidate> elements, String action, String optionTarget, String ownerContext, String location) {
        String composite = (ownerContext == null || ownerContext.isBlank())
                ? optionTarget
                : optionTarget + " under " + ownerContext;
        return rank(elements, action, composite, location);
    }

    static String extractOwnerHint(String hint) {
        if (hint == null) {
            return "";
        }
        String h = hint.toLowerCase(Locale.ROOT);
        int under = h.indexOf(" under ");
        if (under > 0) {
            return h.substring(under + 7).trim();
        }
        int in = h.indexOf(" in ");
        if (in > 0 && !h.contains("sign in") && !h.contains("log in")) {
            return h.substring(in + 4).trim();
        }
        return "";
    }

    static String extractOptionHint(String hint, String ownerHint) {
        if (hint == null) {
            return "";
        }
        String h = hint.trim();
        if (ownerHint == null || ownerHint.isBlank()) {
            return normalize(h);
        }
        String lower = h.toLowerCase(Locale.ROOT);
        int under = lower.indexOf(" under ");
        if (under > 0) {
            return normalize(h.substring(0, under));
        }
        int in = lower.indexOf(" in ");
        if (in > 0 && !lower.contains("sign in") && !lower.contains("log in")) {
            return normalize(h.substring(0, in));
        }
        return normalize(h);
    }

    public static boolean optionMatches(ElementCandidate element, String optionHint) {
        if (optionHint == null || optionHint.isBlank() || element == null) {
            return false;
        }
        String blob = normalize(String.join(" ",
                element.accessibleName(), element.text(), element.label(), element.ariaLabel(), element.value()));
        if (blob.isBlank()) {
            return false;
        }
        if (blob.equals(optionHint)) {
            return true;
        }
        // Short option codes ("AK") must match a token, not a page-wide substring.
        if (optionHint.length() <= 3) {
            for (String token : blob.split(" ")) {
                if (token.equals(optionHint)) {
                    return true;
                }
            }
            return false;
        }
        return blob.contains(optionHint);
    }

    private static double relationalBoost(ElementCandidate element, String hint) {
        String lower = hint.toLowerCase(Locale.ROOT);
        double boost = 0;
        if (lower.contains(" next to ") || lower.contains(" beside ") || lower.contains(" near ")) {
            String[] parts = lower.split(" next to | beside | near ", 2);
            if (parts.length == 2) {
                String neighbor = normalize(parts[1]);
                String siblings = normalize(element.siblingContext() + " " + element.nearbyText());
                if (!neighbor.isBlank() && siblings.contains(neighbor)) {
                    boost += 45;
                }
            }
        }
        if (lower.contains(" in ")) {
            String[] parts = lower.split(" in ", 2);
            if (parts.length == 2) {
                String section = normalize(parts[1]);
                if (!section.isBlank() && DomEvidence.ownsContext(element, section)) {
                    boost += 50;
                }
            }
        }
        return boost;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static String candidatePrefix(String hint) {
        if (hint == null) {
            return "ELEMENT";
        }
        if (hint.contains("profile") || hint.contains("account") || hint.contains("user")) {
            return "PROFILE";
        }
        if (hint.contains("login") || hint.contains("sign in")) {
            return "LOGIN";
        }
        if (hint.contains("filter") || hint.contains("brand") || hint.contains("price")) {
            return "FILTER";
        }
        return "ELEMENT";
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static boolean isSubmitLikeHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return false;
        }
        String n = hint.trim();
        return n.equals("search") || n.equals("save") || n.equals("submit") || n.equals("reset")
                || n.equals("apply") || n.equals("add") || n.equals("delete");
    }

    private static Set<String> synonymForms(String hint) {
        Set<String> forms = new java.util.LinkedHashSet<>();
        if (hint == null || hint.isBlank()) {
            return forms;
        }
        forms.add(hint);
        if (hint.contains("login") || hint.equals("log in") || hint.contains("sign in") || hint.contains("signin")) {
            forms.add("login");
            forms.add("log in");
            forms.add("sign in");
            forms.add("signin");
            forms.add("sign in to continue");
        }
        if (hint.contains("profile") || hint.contains("account") || hint.contains("user") || hint.contains("avatar")) {
            forms.add("profile");
            forms.add("account");
            forms.add("my account");
            forms.add("user");
            forms.add("avatar");
            forms.add("profile icon");
        }
        return forms;
    }

    private static boolean containsAny(String value, Set<String> forms) {
        for (String form : forms) {
            if (!form.isBlank() && value.contains(form)) {
                return true;
            }
        }
        return false;
    }

    private static int synonymTokenOverlap(Set<String> forms, String value) {
        int hits = 0;
        for (String form : forms) {
            hits += tokenOverlap(form, value);
        }
        return hits;
    }

    static int tokenOverlap(String hint, String value) {
        int hits = 0;
        for (String token : hint.split(" ")) {
            if (token.length() < 3) {
                continue;
            }
            if (List.of("the", "and", "for", "with", "from", "button", "link", "field").contains(token)) {
                continue;
            }
            if (value.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * Explain why a candidate scored as it did. Uses the same math as {@link #rank}.
     */
    public static ScoreBreakdown explain(ElementCandidate element, String action, String target, String location) {
        if (element == null || target == null) {
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "no candidate");
        }
        HardConstraint hard = HardConstraintChecker.evaluate(element, action, extractOwnerHint(normalize(target)));
        if (hard != null) {
            return ScoreBreakdown.rejected(hard);
        }
        return scoreBreakdown(element, action, normalize(target), location, 0);
    }

    public static boolean uniqueWinner(List<RankedElement> ranked) {
        if (ranked.isEmpty()) {
            return false;
        }
        if (ranked.size() == 1) {
            return ranked.getFirst().score() >= 80;
        }
        return ranked.getFirst().score() >= 80 && ranked.getFirst().score() - ranked.get(1).score() >= 30;
    }

    public static double confidence(double score) {
        if (score >= 220) {
            return 0.95;
        }
        if (score >= 170) {
            return 0.85;
        }
        if (score >= 120) {
            return 0.70;
        }
        return 0.45;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static double iconIntentScore(ElementCandidate element, String action, String hint) {
        if (!"click".equals(action)) {
            return 0;
        }
        if (!isIconHint(hint)) {
            return 0;
        }
        double score = 0;
        String semantic = normalize(String.join(" ",
                element.accessibleName(),
                element.ariaLabel(),
                element.title(),
                element.text(),
                element.className(),
                element.id(),
                element.name(),
                element.testId(),
                element.parentContext(),
                element.nearbyText(),
                element.headingContext(),
                element.ancestorContext(),
                element.siblingContext(),
                element.region(),
                element.actionableTag(),
                element.actionableRole()));
        if (element.hasIcon()) {
            score += 28;
        }
        if (element.clickable()) {
            score += 20;
        }
        if (element.inHeaderRegion()) {
            score += 22;
        }
        if (notBlank(element.actionableSelector())) {
            score += 10;
        }
        if (semanticHit(hint, semantic, "profile", "account", "user", "my account", "login", "sign in", "member")) {
            score += 65;
        }
        if (semanticHit(hint, semantic, "cart", "bag", "basket", "checkout")) {
            score += 60;
        }
        if (semanticHit(hint, semantic, "search", "find")) {
            score += 60;
        }
        if (semanticHit(hint, semantic, "filter", "filters", "facet", "funnel")) {
            score += 60;
        }
        if (semanticHit(hint, semantic, "menu", "hamburger", "navigation", "drawer")) {
            score += 60;
        }
        if (semanticHit(hint, semantic, "close", "dismiss", "cancel")) {
            score += 60;
        }
        if (semanticHit(hint, semantic, "back", "previous", "return")) {
            score += 60;
        }
        if (hint.contains("profile") || hint.contains("account") || hint.contains("user")) {
            // Weak generic labels are common on icon-only controls; prefer rightmost header icons.
            if (semantic.contains("footer") || semantic.contains("follow us") || semantic.contains("social")) {
                score -= 35;
            }
            if (semantic.contains("cart") || semantic.contains("bag") || semantic.contains("basket")
                    || semantic.contains("checkout") || semantic.contains("shopping")) {
                score -= 120;
            }
            if (element.inHeaderRegion() && element.hasIcon() && element.clickable()) {
                score += 18;
            }
            String tag = element.tag() == null ? "" : element.tag().toLowerCase(Locale.ROOT);
            if ("button".equals(tag) || "button".equalsIgnoreCase(element.actionableRole())
                    || "button".equalsIgnoreCase(element.role())) {
                score += 28;
            }
            int boxArea = area(element.boundingBox());
            // Wide wrappers that contain both cart + profile cause center-clicks to hit cart.
            if (boxArea > 4_000) {
                score -= 50;
            }
            if (boxArea > 10_000) {
                score -= 80;
            }
            if (element.boundingBox() != null && !element.boundingBox().isBlank()) {
                int x = left(element.boundingBox());
                if (x > 0) {
                    // Stronger right-edge bias for profile/account when labels are weak.
                    score += Math.min(36, x / 40.0);
                }
            }
            String exactName = normalize(element.accessibleName() + " " + element.ariaLabel() + " " + element.text());
            if (exactName.isBlank()) {
                // Unlabeled compact header icon is often the true profile control.
                score += 30;
            } else if (exactName.contains("double tap to perform action")) {
                // React-Native-Web placeholder aria on the neighboring cart/bag control.
                score -= 45;
            }
        }
        if (hint.contains("cart") || hint.contains("bag") || hint.contains("basket")) {
            if (semantic.contains("profile") || semantic.contains("account") || semantic.contains("user")
                    || semantic.contains("avatar") || semantic.contains("login") || semantic.contains("sign in")) {
                score -= 120;
            }
            if (element.inHeaderRegion() && element.hasIcon() && element.clickable()) {
                score += 18;
            }
        }
        return score;
    }

    private static boolean semanticHit(String hint, String semantic, String... tokens) {
        boolean hintMatches = false;
        for (String token : tokens) {
            if (hint.contains(token)) {
                hintMatches = true;
                break;
            }
        }
        if (!hintMatches) {
            return false;
        }
        for (String token : tokens) {
            if (semantic.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChromeControlHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return false;
        }
        return switch (hint) {
            case "filter", "filters", "menu", "hamburger", "login", "cart", "profile", "close", "search" -> true;
            default -> {
                String[] tokens = hint.split(" ");
                if (tokens.length <= 4) {
                    for (String token : tokens) {
                        if (token.equals("filter") || token.equals("filters") || token.equals("facet")) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
        };
    }

    private static boolean looksLikeLocationField(ElementCandidate element) {
        String blob = normalize(element.accessibleName() + " " + element.placeholder() + " " + element.ariaLabel()
                + " " + element.label() + " " + element.name() + " " + element.id() + " " + element.title());
        return blob.contains("city")
                || blob.contains("location")
                || blob.contains("pincode")
                || blob.contains("pin code")
                || blob.contains("locality")
                || blob.contains("enter city");
    }

    private static boolean looksLikeLocationQuery(String hint) {
        if (hint == null || hint.isBlank()) {
            return false;
        }
        String blob = normalize(hint);
        return blob.contains("city")
                || blob.contains("location")
                || blob.contains("pincode")
                || blob.contains("locality");
    }

    private static boolean isIconHint(String hint) {
        return hint.contains("icon")
                || hint.contains("profile")
                || hint.contains("account")
                || hint.contains("user")
                || hint.contains("cart")
                || hint.contains("search")
                || hint.contains("filter")
                || hint.contains("menu")
                || hint.contains("hamburger")
                || hint.contains("close")
                || hint.contains("back");
    }

    private static int left(String bbox) {
        try {
            String[] parts = bbox.split(",");
            if (parts.length < 4) {
                return 0;
            }
            return Integer.parseInt(parts[0].trim());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static int area(String bbox) {
        try {
            String[] parts = bbox.split(",");
            if (parts.length < 4) {
                return 0;
            }
            return Integer.parseInt(parts[2].trim()) * Integer.parseInt(parts[3].trim());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static double filterActionBoost(ElementCandidate element, String action, String hint) {
        boolean filterAction = "checkbox".equals(action) || "filter".equals(action) || "select".equals(action);
        boolean shortOption = hint != null && !hint.isBlank() && hint.split(" ").length <= 2 && hint.length() <= 16
                && !isChromeControlHint(hint);
        if (!filterAction && !shortOption) {
            return 0;
        }
        double boost = 0;
        String role = element.role() == null ? "" : element.role().toLowerCase(Locale.ROOT);
        String type = element.inputType() == null ? "" : element.inputType().toLowerCase(Locale.ROOT);
        if ("checkbox".equals(role) || "radio".equals(role) || "checkbox".equals(type) || "radio".equals(type)) {
            boost += 80;
        }
        String region = normalize(element.region());
        if ("filter_panel".equals(region) || "sidebar".equals(region)) {
            boost += 80;
        }
        if (element.inHeaderRegion() || "header".equals(region) || "navigation".equals(region) || "footer".equals(region)) {
            boost -= 120;
        }
        return boost;
    }

    private static double visualBoost(ElementCandidate element) {
        String tag = element.tag() == null ? "" : element.tag().toLowerCase(Locale.ROOT);
        double boost = 0;
        if (element.hasIcon()) {
            boost += 12;
        }
        if ("img".equals(tag) || "svg".equals(tag) || "canvas".equals(tag) || "picture".equals(tag)) {
            boost += 20;
        }
        return boost;
    }

    private static double frameBoost(ElementCandidate element) {
        return notBlank(element.iframeContext()) || notBlank(element.frameUrl()) ? 18 : 0;
    }

    private static double shadowBoost(ElementCandidate element) {
        return notBlank(element.shadowContext()) ? 18 : 0;
    }

    private static double treePathScore(ElementCandidate element, String ownerHint) {
        if (ownerHint == null || ownerHint.isBlank()) {
            return 0;
        }
        String path = element.structureOrEmpty().treePath();
        if (path != null && path.toLowerCase(Locale.ROOT).contains(ownerHint.toLowerCase(Locale.ROOT))) {
            return 25;
        }
        return 0;
    }

    private static double axNameScore(ElementCandidate element, String hint) {
        String ax = element.structureOrEmpty().axName();
        if (ax == null || ax.isBlank() || hint == null || hint.isBlank()) {
            return 0;
        }
        String n = normalize(ax);
        if (n.equals(hint)) {
            return 20;
        }
        return n.contains(hint) || hint.contains(n) ? 10 : 0;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null && !haystack.isBlank() && !needle.isBlank()
                && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean looksLikeRange(String hint) {
        if (hint == null) {
            return false;
        }
        String n = hint.toLowerCase(Locale.ROOT);
        return n.contains(" to ") || n.contains("between") || n.contains("min") || n.contains("max")
                || n.contains("price") || n.contains("range");
    }

    private static boolean looksLikeRangeField(ElementCandidate element) {
        if (element == null) {
            return false;
        }
        String blob = normalize(element.role() + " " + element.inputType() + " " + element.tag()
                + " " + element.accessibleName());
        return "range".equals(element.inputType()) || blob.contains("slider") || blob.contains("range")
                || blob.contains("price") || blob.contains("min") || blob.contains("max");
    }
}
