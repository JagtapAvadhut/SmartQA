package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.BrowserSnapshot;
import com.smartqa.browser.intelligence.ControlType;
import com.smartqa.browser.intelligence.DomEvidence;
import com.smartqa.browser.intelligence.ElementCandidate;
import com.smartqa.browser.intelligence.LocatorRanker;
import com.smartqa.browser.intelligence.RelevantDomExtractor;
import com.smartqa.browser.intelligence.StateSnapshot;
import com.smartqa.browser.multimodal.CandidateRelationshipGraph;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.intent.IntentFilter;
import com.smartqa.intent.SupportedActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generic filter discovery / ownership / expansion / verification.
 * No website-specific selectors.
 */
public final class FilterEngine {

    public record FilterOption(
            String optionId,
            String label,
            String value,
            boolean selected,
            boolean disabled,
            String parentFilterId,
            String nodeId
    ) {
    }

    public record FilterContainer(
            String containerId,
            String name,
            String region,
            boolean expanded,
            List<FilterOption> options
    ) {
    }

    public record ResultState(
            boolean optionSelected,
            boolean resultsChanged,
            String evidence
    ) {
    }

    public record FilterDefinition(
            String filterId,
            String name,
            String containerNodeId,
            String controlType,
            boolean expanded,
            List<FilterOption> options
    ) {
    }

    public record Discovery(
            ElementCandidate fieldCandidate,
            ElementCandidate optionCandidate,
            String bindAction,
            String field,
            String value,
            String operator,
            FilterDefinition definition
    ) {
    }

    private final BrowserIntelligenceService intelligence;
    private final ElementResolver elementResolver;
    private final com.smartqa.browser.multimodal.MultimodalTargetDiscoveryEngine multimodal;

    public FilterEngine(BrowserIntelligenceService intelligence, ElementResolver elementResolver) {
        this(intelligence, elementResolver, null);
    }

    public FilterEngine(
            BrowserIntelligenceService intelligence,
            ElementResolver elementResolver,
            com.smartqa.browser.multimodal.MultimodalTargetDiscoveryEngine multimodal) {
        this.intelligence = intelligence;
        this.elementResolver = elementResolver;
        this.multimodal = multimodal;
    }

    public Discovery discover(Page page, String field, String operator, String value) {
        BrowserSnapshot snapshot = intelligence.inspect(page, List.of());
        String fieldHint = humanize(field);
        String optionHint = value == null || value.isBlank() ? fieldHint : value.trim();

        ElementCandidate fieldCandidate = findFilterContainer(snapshot.elements(), fieldHint);
        ElementCandidate optionCandidate = null;
        if (looksLikeRangeField(fieldHint)) {
            ElementCandidate nativeSelect = findNativeSelect(snapshot.elements(), fieldHint);
            if (nativeSelect != null) {
                fieldCandidate = nativeSelect;
                optionCandidate = nativeSelect;
            }
        }
        List<ElementCandidate> searchSpace = snapshot.elements();
        if (fieldCandidate != null) {
            List<ElementCandidate> descendants = CandidateRelationshipGraph.descendantsOf(
                    snapshot.elements(), fieldCandidate);
            if (!descendants.isEmpty()) {
                searchSpace = descendants;
            }
        }
        List<ElementCandidate> owned = RelevantDomExtractor.ownedOptions(
                searchSpace, optionHint, fieldHint);
        if (owned.isEmpty() && fieldCandidate != null) {
            owned = CandidateRelationshipGraph.childrenOf(snapshot.elements(), fieldHint);
        }
        if (optionCandidate == null && !owned.isEmpty()) {
            String rankAction = ownedLooksLikeCheckbox(owned) ? SupportedActions.CHECKBOX : SupportedActions.CLICK;
            List<LocatorRanker.RankedElement> ownedRanked = LocatorRanker.rankOwned(
                    owned, rankAction, optionHint, fieldHint, null);
            if (!ownedRanked.isEmpty()) {
                optionCandidate = ownedRanked.getFirst().element();
            } else {
                optionCandidate = owned.getFirst();
            }
        }

        if (optionCandidate == null) {
            String rankAction = ownedLooksLikeCheckbox(searchSpace) ? SupportedActions.CHECKBOX : SupportedActions.CLICK;
            List<LocatorRanker.RankedElement> optionRanked = LocatorRanker.rankOwned(
                    searchSpace, rankAction, optionHint, fieldHint, null);
            for (LocatorRanker.RankedElement ranked : optionRanked) {
                ElementCandidate el = ranked.element();
                if (DomEvidence.ownsContext(el, fieldHint) || isFilterControl(el, fieldHint)) {
                    optionCandidate = el;
                    break;
                }
            }
        }

        if (optionCandidate == null && multimodal != null) {
            try {
                var outcome = multimodal.discover(
                        page, SupportedActions.CLICK, optionHint + " under " + fieldHint, snapshot,
                        List.of(), "FILTER_RESOLUTION_FAILURE", List.of());
                if (outcome.accepted() && outcome.ranked().isPresent()) {
                    optionCandidate = outcome.ranked().get().element();
                }
            } catch (RuntimeException ignored) {
            }
        }

        if (fieldCandidate == null && optionCandidate != null) {
            fieldCandidate = syntheticFieldFromOption(optionCandidate, fieldHint);
        }

        String bindAction = inferBindAction(optionCandidate);
        FilterDefinition definition = buildDefinition(fieldHint, fieldCandidate, optionCandidate, snapshot.elements());

        TraceLogger.info("FILTER", "FILTER_DISCOVERY", "Discovered filter candidates", TraceMeta.of(
                "field", fieldHint,
                "operator", operator == null ? "equals" : operator,
                "value", optionHint,
                "fieldCandidate", fieldCandidate == null ? "" : fieldCandidate.candidateId(),
                "optionCandidate", optionCandidate == null ? "" : optionCandidate.candidateId(),
                "bindAction", bindAction,
                "ownedOptionCount", owned.size(),
                "controlType", definition == null ? "" : definition.controlType(),
                "region", optionCandidate == null ? "" : optionCandidate.region(),
                "headingContext", optionCandidate == null ? "" : optionCandidate.headingContext(),
                "ancestorContext", optionCandidate == null ? "" : truncate(optionCandidate.ancestorContext(), 120)
        ));

        if (optionCandidate == null && fieldCandidate == null) {
            throw new SmartQaException(ErrorCode.FILTER_APPLICATION_FAILURE,
                    "Unable to discover filter controls for field=" + fieldHint + " value=" + optionHint);
        }
        return new Discovery(fieldCandidate, optionCandidate, bindAction, fieldHint, optionHint,
                operator == null ? "equals" : operator, definition);
    }

    /**
     * Expand collapsed filter sections when aria-expanded=false or options not yet owned.
     * Does not click an already-open section (that would collapse it).
     */
    public boolean ensureExpanded(Page page, Discovery discovery) {
        if (discovery == null || discovery.fieldCandidate() == null) {
            return false;
        }
        ElementCandidate field = discovery.fieldCandidate();
        ElementCandidate option = discovery.optionCandidate();
        if (option != null && option.visible()) {
            return true;
        }
        if (field.ariaExpanded()) {
            return true;
        }
        try {
            StateSnapshot before = StateSnapshot.capture(page, 0);
            String hint = firstNonBlank(field.headingContext(), field.accessibleName(), field.text(), discovery.field());
            clickExpander(page, hint);
            StateSnapshot after = StateSnapshot.capture(page, 0);
            boolean changed = before.meaningfullyDifferent(after);
            if (!changed) {
                Locator byText = page.getByText(hint, new Page.GetByTextOptions().setExact(true));
                if (byText.count() > 0 && byText.first().isVisible()) {
                    SafeClick.click(byText.first(), page);
                    after = StateSnapshot.capture(page, 0);
                    changed = before.meaningfullyDifferent(after);
                }
            }
            TraceLogger.info("FILTER", "FILTER_EXPANDED", "Attempted filter expand", TraceMeta.of(
                    "field", discovery.field(),
                    "stateChanged", changed
            ));
            return changed || field.ariaExpanded();
        } catch (RuntimeException ex) {
            TraceLogger.warn("FILTER", "FILTER_EXPAND_FAILED", "Could not expand filter", TraceMeta.of(
                    "field", discovery.field(),
                    "message", ex.getMessage() == null ? "" : ex.getMessage()
            ));
            return false;
        }
    }

    /**
     * Verify filter selection produced a meaningful selected/result state change.
     */
    public boolean verifySelection(Page page, Discovery discovery, StateSnapshot before) {
        BrowserSnapshot snapshot = intelligence.inspect(page, List.of());
        boolean selected = false;
        if (discovery != null && discovery.value() != null) {
            for (ElementCandidate el : RelevantDomExtractor.ownedOptions(
                    snapshot.elements(), discovery.value(), discovery.field())) {
                if (el.checked() || el.selected()) {
                    selected = true;
                    break;
                }
            }
        }
        StateSnapshot after = StateSnapshot.capture(page, snapshot.interactiveCount());
        boolean changed = before != null && before.meaningfullyDifferent(after);
        StateSnapshot.Diff diff = StateSnapshot.diff(before, after);
        TraceLogger.info("FILTER", "FILTER_RESULT_VERIFY", "Verified filter application", TraceMeta.of(
                "field", discovery == null ? "" : discovery.field(),
                "value", discovery == null ? "" : discovery.value(),
                "selected", selected,
                "stateChanged", changed,
                "urlChanged", diff.urlChanged(),
                "textChanged", diff.visibleTextChanged(),
                "interactiveDelta", diff.interactiveDelta()
        ));
        return selected || changed;
    }

    public ElementResolver.ResolvedElement resolveOption(Page page, Discovery discovery) {
        String target = discovery.value() == null || discovery.value().isBlank()
                ? discovery.field()
                : discovery.value() + " under " + discovery.field();
        return elementResolver.resolve(page, discovery.bindAction(), target);
    }

    public ElementResolver.ResolvedElement resolveRangeBound(Page page, String field, boolean min) {
        String hint = (min ? "Min " : "Max ") + humanize(field);
        String owned = hint + " under " + humanize(field);
        try {
            return elementResolver.resolve(page, SupportedActions.INPUT, owned);
        } catch (RuntimeException ignored) {
            return elementResolver.resolve(page, SupportedActions.SELECT, owned);
        }
    }

    public static String describe(IntentFilter filter, String fallbackField, String fallbackValue) {
        if (filter == null) {
            return fallbackField + "=" + fallbackValue;
        }
        if ("between".equalsIgnoreCase(filter.operator()) && filter.min() != null && filter.max() != null) {
            return filter.field() + " between " + filter.min().longValue() + " and " + filter.max().longValue();
        }
        return (filter.field() == null ? fallbackField : filter.field())
                + " " + (filter.operator() == null ? "equals" : filter.operator())
                + " " + (filter.value() == null ? fallbackValue : filter.value());
    }

    public static ControlType inferControlType(String bindAction) {
        return switch (bindAction == null ? "" : bindAction.toLowerCase(Locale.ROOT)) {
            case SupportedActions.INPUT -> ControlType.TEXTBOX;
            case SupportedActions.SELECT -> ControlType.COMBOBOX;
            case SupportedActions.CHECKBOX -> ControlType.CHECKBOX;
            case SupportedActions.RADIO -> ControlType.RADIO;
            default -> ControlType.BUTTON;
        };
    }

    private static ElementCandidate findFilterContainer(List<ElementCandidate> elements, String fieldHint) {
        ElementCandidate best = null;
        double bestScore = -1;
        for (ElementCandidate el : elements) {
            if (!el.visible()) {
                continue;
            }
            double score = 0;
            String blob = el.ownershipContext() + " " + normalize(el.accessibleName()) + " " + normalize(el.text());
            String field = normalize(fieldHint);
            if (blob.contains(field)) {
                score += 50;
            }
            if ("FILTER_PANEL".equalsIgnoreCase(el.region()) || "SIDEBAR".equalsIgnoreCase(el.region())) {
                score += 20;
            }
            if (normalize(el.headingContext()).equals(field) || normalize(el.headingContext()).contains(field)) {
                score += 40;
            }
            String role = el.role() == null ? "" : el.role().toLowerCase(Locale.ROOT);
            if ("button".equals(role) || el.ariaExpanded() || "summary".equalsIgnoreCase(el.tag())) {
                score += 10;
            }
            if (score > bestScore) {
                bestScore = score;
                best = el;
            }
        }
        return bestScore >= 40 ? best : null;
    }

    private static boolean isFilterControl(ElementCandidate el, String fieldHint) {
        String role = el.role() == null ? "" : el.role().toLowerCase(Locale.ROOT);
        if ("checkbox".equals(role) || "radio".equals(role) || "option".equals(role)) {
            return true;
        }
        return DomEvidence.ownsContext(el, fieldHint)
                || "FILTER_PANEL".equalsIgnoreCase(el.region())
                || "SIDEBAR".equalsIgnoreCase(el.region());
    }

    private void clickExpander(Page page, String hint) {
        if (hint == null || hint.isBlank()) {
            return;
        }
        try {
            Locator summary = page.locator("summary").filter(new Locator.FilterOptions().setHasText(hint));
            if (summary.count() > 0 && summary.first().isVisible()) {
                SafeClick.click(summary.first(), page);
                return;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            ElementResolver.ResolvedElement expander = elementResolver.resolve(page, SupportedActions.CLICK, hint);
            SafeClick.click(expander.locator(), page);
            return;
        } catch (RuntimeException ignored) {
        }
        try {
            Locator byRole = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(hint));
            if (byRole.count() > 0 && byRole.first().isVisible()) {
                SafeClick.click(byRole.first(), page);
                return;
            }
        } catch (RuntimeException ignored) {
        }
        Locator byText = page.getByText(hint, new Page.GetByTextOptions().setExact(true));
        if (byText.count() > 0 && byText.first().isVisible()) {
            SafeClick.click(byText.first(), page);
        }
    }

    private static boolean ownedLooksLikeCheckbox(List<ElementCandidate> candidates) {
        if (candidates == null) {
            return false;
        }
        for (ElementCandidate el : candidates) {
            String role = el.role() == null ? "" : el.role().toLowerCase(Locale.ROOT);
            String type = el.inputType() == null ? "" : el.inputType().toLowerCase(Locale.ROOT);
            if ("checkbox".equals(role) || "checkbox".equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static String inferBindAction(ElementCandidate optionCandidate) {
        if (optionCandidate == null) {
            return SupportedActions.CLICK;
        }
        String role = optionCandidate.role() == null ? "" : optionCandidate.role().toLowerCase(Locale.ROOT);
        String tag = optionCandidate.tag() == null ? "" : optionCandidate.tag().toLowerCase(Locale.ROOT);
        String type = optionCandidate.inputType() == null ? "" : optionCandidate.inputType().toLowerCase(Locale.ROOT);
        if ("slider".equals(role) || "range".equals(type)) {
            return SupportedActions.INPUT;
        }
        if ("textbox".equals(role) || "searchbox".equals(role) || "input".equals(tag) || "textarea".equals(tag)) {
            return SupportedActions.INPUT;
        }
        if ("combobox".equals(role) || "listbox".equals(role) || "select".equals(tag)) {
            return SupportedActions.SELECT;
        }
        if ("checkbox".equals(role) || "checkbox".equals(type)) {
            return SupportedActions.CHECKBOX;
        }
        return SupportedActions.CLICK;
    }

    private static FilterDefinition buildDefinition(
            String fieldHint,
            ElementCandidate fieldCandidate,
            ElementCandidate optionCandidate,
            List<ElementCandidate> all) {
        String filterId = fieldCandidate != null ? fieldCandidate.candidateId() : "filter-" + normalize(fieldHint);
        String controlType = inferControlTypeName(optionCandidate);
        List<FilterOption> options = new ArrayList<>();
        for (ElementCandidate el : all) {
            if (!el.visible() || !DomEvidence.ownsContext(el, fieldHint)) {
                continue;
            }
            if (options.size() >= 40) {
                break;
            }
            String label = firstNonBlank(el.accessibleName(), el.text(), el.label(), el.value());
            if (label.isBlank()) {
                continue;
            }
            options.add(new FilterOption(
                    el.candidateId(),
                    label,
                    el.value().isBlank() ? label : el.value(),
                    el.checked() || el.selected(),
                    el.disabled(),
                    filterId,
                    el.candidateId()
            ));
        }
        if (optionCandidate != null && options.stream().noneMatch(o -> o.nodeId().equals(optionCandidate.candidateId()))) {
            options.add(0, new FilterOption(
                    optionCandidate.candidateId(),
                    firstNonBlank(optionCandidate.accessibleName(), optionCandidate.text(), optionCandidate.value()),
                    optionCandidate.value().isBlank()
                            ? firstNonBlank(optionCandidate.text(), optionCandidate.accessibleName())
                            : optionCandidate.value(),
                    optionCandidate.checked() || optionCandidate.selected(),
                    optionCandidate.disabled(),
                    filterId,
                    optionCandidate.candidateId()
            ));
        }
        return new FilterDefinition(
                filterId,
                fieldHint,
                fieldCandidate == null ? "" : fieldCandidate.candidateId(),
                controlType,
                fieldCandidate != null && fieldCandidate.ariaExpanded(),
                List.copyOf(options)
        );
    }

    private static String inferControlTypeName(ElementCandidate el) {
        if (el == null) {
            return "UNKNOWN";
        }
        String role = el.role() == null ? "" : el.role().toLowerCase(Locale.ROOT);
        String tag = el.tag() == null ? "" : el.tag().toLowerCase(Locale.ROOT);
        String type = el.inputType() == null ? "" : el.inputType().toLowerCase(Locale.ROOT);
        if ("checkbox".equals(role) || "checkbox".equals(type)) return "CHECKBOX";
        if ("radio".equals(role) || "radio".equals(type)) return "RADIO";
        if ("select".equals(tag)) return "NATIVE_SELECT";
        if ("combobox".equals(role)) return "COMBOBOX";
        if ("listbox".equals(role) || "option".equals(role)) return "DROPDOWN";
        if ("textbox".equals(role) || "input".equals(tag)) return "MIN_MAX_INPUT";
        return "CHIP";
    }

    private static ElementCandidate syntheticFieldFromOption(ElementCandidate option, String fieldHint) {
        // Reuse option's structural context as the owning filter section signal.
        return option;
    }

    private static String humanize(String field) {
        if (field == null || field.isBlank()) {
            return "filter";
        }
        return field.replace('_', ' ').replace('-', ' ').trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static boolean looksLikeRangeField(String fieldHint) {
        String blob = normalize(fieldHint);
        return blob.contains("price")
                || blob.contains("min")
                || blob.contains("max")
                || blob.contains("budget")
                || blob.contains("amount")
                || blob.contains("range");
    }

    private static ElementCandidate findNativeSelect(List<ElementCandidate> elements, String fieldHint) {
        for (ElementCandidate el : elements) {
            if (el == null || !el.visible()) {
                continue;
            }
            String tag = el.tag() == null ? "" : el.tag().toLowerCase(Locale.ROOT);
            if (!"select".equals(tag)) {
                continue;
            }
            if (DomEvidence.ownsContext(el, fieldHint) || isFilterControl(el, fieldHint)) {
                return el;
            }
        }
        return null;
    }
}
