package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generic SEARCH STATE CONTRACT. Click/fill success is not enough — requested semantic
 * state must appear in the selected suggestion, input, URL, and result context.
 */
public final class SearchStateContract {

    private static final ThreadLocal<Session> SESSION = new ThreadLocal<>();

    private static final Set<String> GENERIC_TOKENS = Set.of(
            "phone", "phones", "smartphone", "smartphones", "mobile", "mobiles",
            "search", "product", "products", "item", "items", "near", "buy", "shop",
            "online", "india", "the", "and", "for", "with", "from", "in"
    );

    private SearchStateContract() {
    }

    public static void begin() {
        SESSION.set(new Session());
    }

    public static void end() {
        SESSION.remove();
    }

    public static Session current() {
        Session session = SESSION.get();
        if (session == null) {
            session = new Session();
            SESSION.set(session);
        }
        return session;
    }

    public static void rememberNewPageWatch(NewPageTracker.Capture capture, AtomicReference<Page> popupRef) {
        Session session = current();
        session.lastCapture = capture;
        session.lastPopup = popupRef;
    }

    public static void recordSearch(String requested, String observed) {
        Session session = current();
        session.searchRequested = requested;
        session.searchObserved = observed;
        session.searchState = new SearchState(
                requested == null ? "" : requested,
                observed == null ? "" : observed,
                "search",
                SearchState.Phase.INPUT_FILLED,
                SearchState.Phase.NOT_STARTED,
                SearchState.Phase.SEARCH_SUBMITTED,
                "",
                "",
                observed == null ? "" : observed,
                0.0,
                SearchState.VerificationStatus.PENDING,
                Instant.now()
        );
    }

    public static void recordLocation(String requested, String selected) {
        Session session = current();
        session.locationRequested = requested;
        session.locationSelected = selected;
        session.locationObserved = selected;
        session.locationState = LocationState.pending(requested, selected);
    }

    public static boolean looksLikeLocationControl(Locator locator) {
        String blob = normalize(safeAttr(locator, "placeholder")
                + " " + safeAttr(locator, "aria-label")
                + " " + safeAttr(locator, "name")
                + " " + safeAttr(locator, "id")
                + " " + safeAttr(locator, "title")
                + " " + safeAttr(locator, "autocomplete"));
        return blob.contains("city")
                || blob.contains("location")
                || blob.contains("pincode")
                || blob.contains("pin code")
                || blob.contains("locality")
                || blob.contains("enter city");
    }

    public static Snapshot capture(Page page, String requested, String selectedSource) {
        String url = safeUrl(page);
        String host = hostOf(url);
        String inputValue = firstSearchInputValue(page);
        String headings = visibleHeadings(page);
        // Generic-only queries (e.g. "smartphones") must not be verified against marketing chrome.
        String evidence = distinctiveTokens(requested).isEmpty()
                ? normalize(inputValue + " " + url)
                : normalize(inputValue + " " + url + " " + headings);
        double confidence = containsDistinctiveTokens(requested, evidence) ? 0.9 : 0.2;
        return new Snapshot(
                requested,
                inputValue,
                selectedSource == null ? "" : selectedSource,
                url,
                host,
                headings,
                confidence
        );
    }

    public static void verifySearch(Page page, String requested) {
        Snapshot snapshot = capture(page, requested, "search");
        recordSearch(requested, snapshot.selectedValue());
        Session session = current();
        session.searchState = toSearchState(snapshot, SearchState.Phase.RESULTS_READY);
        if (!containsDistinctiveTokens(requested, evidenceOf(snapshot))) {
            session.searchState = session.searchState.withVerification(
                    SearchState.VerificationStatus.MISMATCH, SearchState.Phase.MISMATCH, snapshot.confidence());
            TraceLogger.warn("SEARCH", "SEARCH_STATE_MISMATCH", "Search semantic state did not match requested value",
                    TraceMeta.of(
                            "requestedValue", requested,
                            "selectedValue", snapshot.selectedValue(),
                            "currentUrl", snapshot.currentUrl(),
                            "resultContext", truncate(snapshot.resultContext(), 180),
                            "confidence", snapshot.confidence(),
                            "verificationStatus", "MISMATCH"
                    ));
            throw new SmartQaException(ErrorCode.SEARCH_STATE_MISMATCH,
                    "SEARCH_STATE_MISMATCH: requested '" + requested + "' but page state was '"
                            + truncate(evidenceOf(snapshot), 180) + "'");
        }
        session.searchState = session.searchState.withVerification(
                SearchState.VerificationStatus.VERIFIED, SearchState.Phase.VERIFIED, snapshot.confidence());
        TraceLogger.info("SEARCH", "SEARCH_STATE_VERIFIED", "Search semantic state verified", TraceMeta.of(
                "requestedValue", requested,
                "selectedValue", snapshot.selectedValue(),
                "currentUrl", snapshot.currentUrl(),
                "confidence", snapshot.confidence(),
                "verificationStatus", "VERIFIED"
        ));
    }

    public static void verifyLocation(Page page, String requested) {
        Snapshot snapshot = capture(page, requested, "location");
        recordLocation(requested, snapshot.selectedValue());
        Session session = current();
        String actual = evidenceOf(snapshot);
        if (!containsDistinctiveTokens(requested, actual)) {
            session.locationState = LocationState.pending(requested, snapshot.selectedValue()).mismatch(actual);
            TraceLogger.warn("SEARCH", "LOCATION_STATE_MISMATCH", "Location semantic state did not match requested value",
                    TraceMeta.of(
                            "requestedValue", requested,
                            "selectedValue", snapshot.selectedValue(),
                            "verifiedValue", truncate(actual, 180),
                            "currentUrl", snapshot.currentUrl(),
                            "resultContext", truncate(snapshot.resultContext(), 180)
                    ));
            throw new SmartQaException(ErrorCode.LOCATION_STATE_MISMATCH,
                    "LOCATION_STATE_MISMATCH: requested '" + requested + "' but page state was '"
                            + truncate(actual, 180) + "'");
        }
        session.locationState = LocationState.pending(requested, snapshot.selectedValue()).verified(snapshot.selectedValue());
        TraceLogger.info("SEARCH", "LOCATION_STATE_VERIFIED", "Location semantic state verified", TraceMeta.of(
                "requestedValue", requested,
                "selectedValue", snapshot.selectedValue(),
                "verifiedValue", snapshot.selectedValue(),
                "currentUrl", snapshot.currentUrl()
        ));
    }

    public static boolean isFilterTarget(String target) {
        String blob = normalize(target);
        return blob.equals("filter") || blob.equals("filters") || blob.contains("filter");
    }

    public static void verifyReadyForFilter(Page page) {
        Session session = current();
        if (session.searchState != null && session.searchState.blockingLaterSteps()) {
            throw new SmartQaException(ErrorCode.SEARCH_STATE_MISMATCH,
                    "Cannot apply filter because search verification already failed");
        }
        if (session.locationState != null && session.locationState.blockingLaterSteps()) {
            throw new SmartQaException(ErrorCode.LOCATION_STATE_MISMATCH,
                    "Cannot apply filter because location verification already failed");
        }
        if (session.searchRequested != null && !session.searchRequested.isBlank()) {
            verifySearch(page, session.searchRequested);
        }
        if (session.locationRequested != null && !session.locationRequested.isBlank()) {
            verifyLocation(page, session.locationRequested);
        }
    }

    public static boolean containsDistinctiveTokens(String requested, String actual) {
        if (requested == null || requested.isBlank()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        String actualNorm = normalize(actual);
        List<String> distinctive = distinctiveTokens(requested);
        if (distinctive.isEmpty()) {
            return actualNorm.contains(normalize(requested));
        }
        for (String token : distinctive) {
            if (!actualNorm.contains(token)) {
                return false;
            }
        }
        return !conflicts(requested, actual);
    }

    /**
     * True when observed text introduces a different distinctive token (Samsung vs Micromax).
     */
    public static boolean conflicts(String requested, String actual) {
        List<String> requestedTokens = distinctiveTokens(requested);
        List<String> actualTokens = distinctiveTokens(actual);
        if (requestedTokens.isEmpty() || actualTokens.isEmpty()) {
            return false;
        }
        for (String token : actualTokens) {
            if (requestedTokens.contains(token)) {
                return false;
            }
        }
        return true;
    }

    public static List<String> distinctiveTokens(String requested) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (requested == null) {
            return List.of();
        }
        for (String token : normalize(requested).split(" ")) {
            if (token.length() < 3 || GENERIC_TOKENS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return new ArrayList<>(tokens);
    }

    public record Snapshot(
            String requestedValue,
            String selectedValue,
            String selectedSource,
            String currentUrl,
            String currentHost,
            String resultContext,
            double confidence
    ) {
    }

    public static final class Session {
        String locationRequested;
        String locationSelected;
        String locationObserved;
        String searchRequested;
        String searchObserved;
        SearchState searchState;
        LocationState locationState;
        NewPageTracker.Capture lastCapture;
        AtomicReference<Page> lastPopup;

        public NewPageTracker.Capture lastCapture() {
            return lastCapture;
        }

        public AtomicReference<Page> lastPopup() {
            return lastPopup;
        }

        public SearchState searchState() {
            return searchState;
        }

        public LocationState locationState() {
            return locationState;
        }
    }

    private static SearchState toSearchState(Snapshot snapshot, SearchState.Phase resultPhase) {
        return new SearchState(
                snapshot.requestedValue() == null ? "" : snapshot.requestedValue(),
                snapshot.selectedValue() == null ? "" : snapshot.selectedValue(),
                snapshot.selectedSource() == null ? "" : snapshot.selectedSource(),
                SearchState.Phase.INPUT_FILLED,
                SearchState.Phase.NOT_STARTED,
                resultPhase,
                snapshot.currentUrl() == null ? "" : snapshot.currentUrl(),
                snapshot.currentHost() == null ? "" : snapshot.currentHost(),
                snapshot.resultContext() == null ? "" : snapshot.resultContext(),
                snapshot.confidence(),
                SearchState.VerificationStatus.PENDING,
                Instant.now()
        );
    }

    private static String evidenceOf(Snapshot snapshot) {
        return (snapshot.selectedValue() == null ? "" : snapshot.selectedValue())
                + " " + (snapshot.currentUrl() == null ? "" : snapshot.currentUrl())
                + " " + (snapshot.resultContext() == null ? "" : snapshot.resultContext());
    }

    private static String firstSearchInputValue(Page page) {
        if (page == null) {
            return "";
        }
        try {
            Locator inputs = page.locator("input:visible, textarea:visible, [role='searchbox'], [role='combobox']");
            int count = Math.min(inputs.count(), 12);
            for (int i = 0; i < count; i++) {
                Locator input = inputs.nth(i);
                String value = "";
                try {
                    value = input.inputValue();
                } catch (RuntimeException ignored) {
                }
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    private static String visibleHeadings(Page page) {
        if (page == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try {
            Locator headings = page.locator("h1, h2, [role='heading']");
            int count = Math.min(headings.count(), 12);
            for (int i = 0; i < count; i++) {
                String text = safeText(headings.nth(i));
                if (!text.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(text);
                }
            }
        } catch (RuntimeException ignored) {
        }
        try {
            String title = page.title();
            if (title != null) {
                sb.append(' ').append(title);
            }
        } catch (RuntimeException ignored) {
        }
        return sb.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String safeUrl(Page page) {
        try {
            return page == null || page.url() == null ? "" : page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String hostOf(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeAttr(Locator locator, String name) {
        try {
            String value = locator.getAttribute(name);
            return value == null ? "" : value;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String safeText(Locator locator) {
        try {
            String text = locator.innerText();
            return text == null ? "" : text.trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
