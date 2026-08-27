package com.smartqa.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.options.AriaRole;
import com.smartqa.ai.AiPrompt;
import com.smartqa.ai.AiProvider;
import com.smartqa.ai.AiTelemetry;
import com.smartqa.browser.intelligence.ActionCompatibility;
import com.smartqa.browser.intelligence.BrowserIntelligenceService;
import com.smartqa.browser.intelligence.BrowserSnapshot;
import com.smartqa.browser.intelligence.ControlClassifier;
import com.smartqa.browser.intelligence.ControlType;
import com.smartqa.browser.intelligence.ElementCandidate;
import com.smartqa.browser.intelligence.FailClosedDecision;
import com.smartqa.browser.intelligence.LocatorContract;
import com.smartqa.browser.intelligence.LocatorRanker;
import com.smartqa.browser.intelligence.RankedLocator;
import com.smartqa.clarification.ClarificationRequiredException;
import com.smartqa.clarification.RuntimeClarificationService;
import com.smartqa.execution.RuntimeExecutionContext;
import com.smartqa.browser.multimodal.CandidateRelationshipGraph;
import com.smartqa.browser.multimodal.MultimodalTargetDiscoveryEngine;
import com.smartqa.browser.multimodal.SemanticTargetNormalizer;
import com.smartqa.common.config.SmartQaProperties;
import com.smartqa.common.error.ErrorCode;
import com.smartqa.common.error.SmartQaException;
import com.smartqa.common.json.JsonSupport;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import com.smartqa.pipeline.AiEvidenceBundle;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ElementResolver {

    private final AiProvider aiProvider;
    private final JsonMapper objectMapper;
    private final BrowserIntelligenceService intelligence;
    private final SmartQaProperties properties;
    private final MultimodalTargetDiscoveryEngine multimodal;
    private final com.smartqa.browser.intelligence.memory.ExecutionMemoryService executionMemory;
    private final RuntimeClarificationService clarifications;

    @Autowired
    public ElementResolver(
            AiProvider aiProvider,
            JsonMapper objectMapper,
            BrowserIntelligenceService intelligence,
            SmartQaProperties properties,
            ObjectProvider<MultimodalTargetDiscoveryEngine> multimodal,
            ObjectProvider<com.smartqa.browser.intelligence.memory.ExecutionMemoryService> memory,
            ObjectProvider<RuntimeClarificationService> clarifications) {
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.intelligence = intelligence;
        this.properties = properties == null ? new SmartQaProperties() : properties;
        this.multimodal = multimodal == null ? null : multimodal.getIfAvailable();
        this.executionMemory = memory == null ? null : memory.getIfAvailable();
        this.clarifications = clarifications == null ? null : clarifications.getIfAvailable();
    }

    public ElementResolver(
            AiProvider aiProvider,
            JsonMapper objectMapper,
            BrowserIntelligenceService intelligence,
            SmartQaProperties properties,
            ObjectProvider<MultimodalTargetDiscoveryEngine> multimodal) {
        this(aiProvider, objectMapper, intelligence, properties, multimodal, null, null);
    }

    public ElementResolver(
            AiProvider aiProvider,
            JsonMapper objectMapper,
            BrowserIntelligenceService intelligence,
            SmartQaProperties properties) {
        this(aiProvider, objectMapper, intelligence, properties, null);
    }

    /** Test helper when SmartQaProperties is not wired. */
    public ElementResolver(
            AiProvider aiProvider,
            JsonMapper objectMapper,
            BrowserIntelligenceService intelligence) {
        this(aiProvider, objectMapper, intelligence, new SmartQaProperties(), null);
    }

    public ResolvedElement resolve(Page page, String action, String target) {
        return resolve(page, action, target, null, intelligence.inspect(page, List.of()));
    }

    public ResolvedElement resolve(Page page, String action, String target, BrowserSnapshot snapshot) {
        return resolve(page, action, target, null, snapshot);
    }

    public ResolvedElement resolve(Page page, String action, String target, String location, BrowserSnapshot snapshot) {
        return resolve(page, action, target, location, snapshot, null);
    }

    public ResolvedElement resolve(
            Page page,
            String action,
            String target,
            String location,
            BrowserSnapshot snapshot,
            String containerContext) {
        if (target == null || target.isBlank()) {
            throw new SmartQaException(ErrorCode.ELEMENT_NOT_FOUND, "Step is missing a target");
        }
        TraceLogger.info("LOCATOR", "ACTION_TARGET_RESOLUTION_STARTED", "Resolving action target", TraceMeta.of(
                "action", action,
                "instruction", target,
                "location", location == null || location.isBlank() ? "AUTO" : location,
                "url", snapshot == null ? null : snapshot.url()
        ));
        SemanticTargetNormalizer.NormalizedTarget intent = SemanticTargetNormalizer.normalize(action, target);
        String loc = location;
        if ((loc == null || loc.isBlank() || "AUTO".equalsIgnoreCase(loc))
                && intent.location() != null && !intent.location().isBlank()) {
            loc = intent.location();
        }
        CandidateRelationshipGraph.Graph graph = snapshot == null ? CandidateRelationshipGraph.build(List.of())
                : snapshot.graphOrBuild();
        List<ElementCandidate> scope = snapshot == null ? List.of() : snapshot.elements();
        String explicitContext = firstNonBlank(containerContext, intent.semanticField());
        boolean contextRequired = (containerContext != null && !containerContext.isBlank())
                || (intent.isFilterOption() && notBlank(intent.semanticField()));
        if (explicitContext != null && !explicitContext.isBlank() && snapshot != null) {
            ElementCandidate owner = snapshot.treeOrBuild().findByHint(snapshot.elements(), explicitContext);
            if (owner != null) {
                List<ElementCandidate> owned = CandidateRelationshipGraph.descendantsOf(
                        snapshot.elements(), graph, owner);
                if (owned.isEmpty()) {
                    owned = CandidateRelationshipGraph.childrenOf(snapshot.elements(), explicitContext);
                }
                scope = owned;
            } else {
                List<ElementCandidate> owned = CandidateRelationshipGraph.childrenOf(
                        snapshot.elements(), explicitContext);
                if (!owned.isEmpty()) {
                    scope = owned;
                } else if (contextRequired) {
                    scope = List.of();
                }
            }
        }
        List<LocatorRanker.RankedElement> ranked;
        double historyScore = advisoryHistoryScore(snapshot, action, target);
        if (intent.isFilterOption()) {
            ranked = LocatorRanker.rankOwned(
                    scope, action, intent.value(), intent.semanticField(), loc);
            ranked = retainOptionMatches(ranked, intent.value());
            if (ranked.isEmpty() && snapshot != null && !contextRequired) {
                ranked = LocatorRanker.rank(snapshot.elements(), action, intent.ownedHint(), loc, historyScore);
                ranked = retainOptionMatches(ranked, intent.value());
            }
        } else {
            ranked = LocatorRanker.rank(scope, action, target, loc, historyScore);
        }
        TraceLogger.info("LOCATOR", "GRAPH_SCOPE", "Candidate graph scoped resolution", TraceMeta.of(
                "scope", scope.size(),
                "graphEdges", graph.edges() == null ? 0 : graph.edges().size(),
                "filterOption", intent.isFilterOption()
        ));
        TraceLogger.info("LOCATOR", "ELEMENT_CANDIDATES_FOUND", "Locator candidates ranked", TraceMeta.of(
                "count", ranked.size(),
                "candidates", candidateSummaries(ranked)
        ));
        emitIntentCandidateTrace(action, target, ranked);
        if (intent.isVisual() && !rankedDomContainsVisualValue(ranked, intent.value())) {
            TraceLogger.info("AI", "VISUAL_DOM_UNRESOLVED",
                    "Visual instruction is not represented as DOM text; escalating to screenshot AI", TraceMeta.of(
                            "action", action,
                            "target", target,
                            "targetType", intent.targetType(),
                            "value", intent.value(),
                            "rankedCount", ranked.size()
                    ));
            ResolvedElement visualResolved = multimodalResolve(
                    page, action, target, snapshot, ranked, "VISUAL_DOM_UNRESOLVED");
            if (visualResolved != null) {
                ControlType controlType = ControlClassifier.classify(visualResolved.locator());
                if (ActionCompatibility.isCompatible(action, controlType) || controlType == ControlType.OTHER) {
                    return emitIntentSelected(visualResolved.withControlType(controlType), action, target);
                }
            }
            TraceLogger.warn("AI", "VISUAL_ESCALATION_UNRESOLVED",
                    "Visual escalation did not yield a live locator; continuing deterministic path", TraceMeta.of(
                            "target", target,
                            "targetType", intent.targetType()
                    ));
        }
        if (FailClosedDecision.equallySupportedDuplicates(ranked, target)) {
            return resolveAmbiguousByClarification(page, action, target, ranked, snapshot);
        }
        if (needsAiBeforeAction(ranked, action, target)) {
            LocatorRanker.RankedElement preferred = aiAssistBeforeAction(page, action, target, snapshot, ranked);
            if (preferred != null) {
                ResolvedElement assisted = firstVerified(page, preferred.element(), preferred.locators());
                if (assisted != null) {
                    ControlType controlType = ControlClassifier.classify(assisted.locator());
                    if (ActionCompatibility.isCompatible(action, controlType) || controlType == ControlType.OTHER) {
                        TraceLogger.info("AI", "AI_BEFORE_ACTION_ACCEPTED", "AI candidate accepted by Safety Gate", TraceMeta.of(
                                "target", target,
                                "locator", assisted.resolvedLocator(),
                                "confidence", assisted.confidence()
                        ));
                        return emitIntentSelected(
                                assisted.withControlType(controlType).withCloud(cloud(preferred.locators())),
                                action, target);
                    }
                }
                TraceLogger.warn("AI", "AI_BEFORE_ACTION_REJECTED", "AI candidate failed live Safety Gate", TraceMeta.of(
                        "target", target
                ));
            }
        }
        for (LocatorRanker.RankedElement candidate : ranked) {
            if (LocatorRanker.confidence(candidate.score()) < 0.70 && candidate.score() < 100) {
                continue;
            }
            if (isFillAction(action) && candidate.element().isTabularChrome()) {
                continue;
            }
            if (isFillAction(action) && candidate.element().isButtonLike()) {
                ResolvedElement associatedButton = findAssociatedControl(
                        page, action, target, candidate.element(), snapshot);
                if (associatedButton != null) {
                    return emitIntentSelected(associatedButton.withCloud(cloud(candidate.locators())), action, target);
                }
                continue;
            }
            ResolvedElement fromDom = firstVerified(page, candidate.element(), candidate.locators());
            if (fromDom == null && shouldSearchAssociatedControl(action, candidate.element())) {
                ResolvedElement associatedEarly = findAssociatedControl(page, action, target, candidate.element(), snapshot);
                if (associatedEarly != null) {
                    return associatedEarly.withCloud(cloud(candidate.locators()));
                }
                if (isFillAction(action)) {
                    ResolvedElement byLabel = findInputByLabel(page, target);
                    if (byLabel != null) {
                        return byLabel.withCloud(cloud(candidate.locators()));
                    }
                }
            }
            if (fromDom != null && !matchesTarget(fromDom.locator(), target)) {
                TraceLogger.info("LOCATOR", "RANKED_CANDIDATE_REJECTED",
                        "Ranked candidate did not match the requested target", TraceMeta.of(
                                "target", target,
                                "locator", fromDom.resolvedLocator(),
                                "visibleText", safeInnerText(fromDom.locator()),
                                "score", candidate.score()
                        ));
                continue;
            }
            if (fromDom != null) {
                ControlType controlType = ControlClassifier.classify(fromDom.locator());
                TraceLogger.info("CONTROL", "CONTROL_CLASSIFIED", "Control type classified", TraceMeta.of(
                        "controlType", controlType.name(),
                        "locator", fromDom.resolvedLocator(),
                        "action", action
                ));

                if (ActionCompatibility.requiresRediscovery(action, controlType)) {
                    TraceLogger.info("LOCATOR", "LABEL_CONTROL_REDISCOVERY",
                            "Resolved to non-interactive element, searching for associated control",
                            TraceMeta.of("resolvedType", controlType.name(), "action", action, "target", target));
                    ResolvedElement associated = findAssociatedControl(page, action, target, candidate.element(), snapshot);
                    if (associated != null) {
                        return associated.withCloud(cloud(candidate.locators()));
                    }
                    if ("select".equalsIgnoreCase(action)) {
                        ResolvedElement dropdown = findDropdownByLabel(page, target);
                        if (dropdown != null) {
                            return dropdown.withCloud(cloud(candidate.locators()));
                        }
                    }
                } else if (!ActionCompatibility.isCompatible(action, controlType)) {
                    TraceLogger.warn("LOCATOR", "ACTION_ELEMENT_MISMATCH",
                            "Action incompatible with resolved control type",
                            TraceMeta.of("action", action, "controlType", controlType.name(),
                                    "locator", fromDom.resolvedLocator()));
                    if (isFillAction(action)) {
                        ResolvedElement associated = findAssociatedControl(page, action, target, candidate.element(), snapshot);
                        if (associated != null) {
                            return emitIntentSelected(associated.withCloud(cloud(candidate.locators())), action, target);
                        }
                        ResolvedElement byLabel = findInputByLabel(page, target);
                        if (byLabel != null) {
                            return emitIntentSelected(byLabel.withCloud(cloud(candidate.locators())), action, target);
                        }
                    }
                } else {
                    TraceLogger.info("LOCATOR", "ACTION_COMPATIBILITY_VERIFIED",
                            "Action compatible with control", TraceMeta.of(
                                    "action", action, "controlType", controlType.name()));
                    return emitIntentSelected(
                            fromDom.withControlType(controlType).withCloud(cloud(candidate.locators())),
                            action, target);
                }
            }
        }

        // If top candidate was a label, try to find its associated control via DOM data
        for (LocatorRanker.RankedElement candidate : ranked) {
            if (candidate.element().isLabel() && candidate.element().hasAssociatedControl()) {
                ResolvedElement associated = findAssociatedControl(page, action, target, candidate.element(), snapshot);
                if (associated != null) {
                    return associated;
                }
            }
        }

        if ("select".equalsIgnoreCase(action)) {
            ResolvedElement labelAssociated = findDropdownByLabel(page, target);
            if (labelAssociated != null) {
                TraceLogger.info("LOCATOR", "LOCATOR_SELECTED", "Found dropdown via label proximity search", TraceMeta.of(
                        "target", target,
                        "locator", labelAssociated.resolvedLocator(),
                        "controlType", labelAssociated.controlType() != null ? labelAssociated.controlType().name() : "UNKNOWN"
                ));
                return labelAssociated;
            }
        }
        if (isFillAction(action)) {
            ResolvedElement labeledInput = findInputByLabel(page, target);
            if (labeledInput != null) {
                TraceLogger.info("LOCATOR", "LOCATOR_SELECTED", "Found input via label proximity search", TraceMeta.of(
                        "target", target,
                        "locator", labeledInput.resolvedLocator(),
                        "controlType", labeledInput.controlType() != null ? labeledInput.controlType().name() : "UNKNOWN"
                ));
                return labeledInput;
            }
        }
        ResolvedElement exactAuth = resolveExactAuthControl(page, action, target);
        if (exactAuth != null) {
            return emitIntentSelected(exactAuth, action, target);
        }
        for (LocatorCandidateFactory.Candidate candidate : LocatorCandidateFactory.candidates(action, target)) {
            Locator locator = toLocator(page, candidate);
            if (isUniqueVisible(locator) && !matchesTarget(locator, target)) {
                TraceLogger.info("LOCATOR", "FACTORY_CANDIDATE_REJECTED",
                        "Unique candidate did not match the requested target", TraceMeta.of(
                                "target", target,
                                "locatorType", candidate.locatorType(),
                                "locator", candidate.resolvedLocator(),
                                "visibleText", safeInnerText(locator)
                        ));
            }
            if (isUniqueVisible(locator) && matchesTarget(locator, target)) {
                ControlType ct = ControlClassifier.classify(locator);
                if (ActionCompatibility.isCompatible(action, ct) || ct == ControlType.OTHER) {
                    return emitIntentSelected(new ResolvedElement(candidate.locatorType(), candidate.resolvedLocator(),
                            candidate.confidence(), false, locator, null, ct, "main", "", "", "", ""), action, target);
                }
            }
            if ("click".equalsIgnoreCase(action) && "text".equals(candidate.locatorType())) {
                ResolvedElement leaf = resolveExactTextLeaf(page, candidate.resolvedLocator());
                if (leaf != null) {
                    return emitIntentSelected(leaf, action, target);
                }
            }
        }
        if (ambiguous(ranked)) {
            LocatorRanker.RankedElement preferred = aiAssistBeforeAction(page, action, target, snapshot, ranked);
            if (preferred != null) {
                ResolvedElement assisted = firstVerified(page, preferred.element(), preferred.locators());
                if (assisted != null) {
                    ControlType controlType = ControlClassifier.classify(assisted.locator());
                    if (ActionCompatibility.isCompatible(action, controlType) || controlType == ControlType.OTHER) {
                        return emitIntentSelected(
                                assisted.withControlType(controlType).withCloud(cloud(preferred.locators())),
                                action, target);
                    }
                }
            }
            List<String> options = intelligence.ambiguityOptions(ranked, target);
            if (options.isEmpty()) {
                options = intelligence.ambiguityOptions(snapshot, action, target);
            }
            ErrorCode code = intent.isFilterOption() ? ErrorCode.FILTER_TARGET_RESOLUTION : ErrorCode.AMBIGUOUS_ELEMENT;
            String prefix = intent.isFilterOption() ? "FILTER_TARGET_RESOLUTION: " : "";
            throw new SmartQaException(
                    code,
                    prefix + "Multiple matching elements for '" + target + "': " + String.join(", ", options)
            );
        }
        ResolvedElement singleControl = singleInteractiveControl(page, action, target);
        if (singleControl != null) {
            return singleControl;
        }
        TraceLogger.info("AI", "DETERMINISTIC_RESOLUTION_INSUFFICIENT",
                "Deterministic DOM resolution did not verify the target", TraceMeta.of(
                        "action", action,
                        "target", target,
                        "rankedCount", ranked == null ? 0 : ranked.size()
                ));
        ResolvedElement aiResolved = multimodalResolve(page, action, target, snapshot, ranked, "TARGET_NOT_FOUND");
        if (aiResolved == null) {
            aiResolved = aiFallback(page, action, target, snapshot);
        }
        if (aiResolved != null) {
            if ("select".equalsIgnoreCase(action)
                    && (aiResolved.controlType() == ControlType.LABEL || "text".equals(aiResolved.locatorType()))) {
                ResolvedElement dropdown = findDropdownByLabel(page, target);
                if (dropdown != null) {
                    TraceLogger.info("LOCATOR", "LOCATOR_SELECTED", "Found dropdown via label association instead of label text", TraceMeta.of(
                            "target", target,
                            "locator", dropdown.resolvedLocator(),
                            "controlType", dropdown.controlType() != null ? dropdown.controlType().name() : "UNKNOWN"
                    ));
                    return dropdown;
                }
            }
            TraceLogger.info("LOCATOR", "LOCATOR_SELECTED", "Locator selected by AI fallback", TraceMeta.of(
                    "locator", aiResolved.resolvedLocator(),
                    "type", aiResolved.locatorType(),
                    "confidence", aiResolved.confidence(),
                    "verified", true
            ));
            return aiResolved;
        }
        if (isProfileIconHint(action, target)) {
            TraceLogger.warn("LOCATOR", "PROFILE_ICON_DISCOVERY_FAILED",
                    "Unable to discover profile icon candidate",
                    TraceMeta.of("candidateCount", ranked.size(), "candidates", candidateSummaries(ranked), "target", target));
        }
        throw new SmartQaException(ErrorCode.TARGET_NOT_PRESENT,
                "The requested target could not be verified in the live application: " + target);
    }

    private ResolvedElement resolveExactAuthControl(Page page, String action, String target) {
        if (!"click".equalsIgnoreCase(action) || target == null) {
            return null;
        }
        String hint = target.toLowerCase(Locale.ROOT).trim();
        if (!(hint.equals("login") || hint.equals("log in") || hint.equals("signin") || hint.equals("sign in"))) {
            return null;
        }
        String[] names = {"Login", "Log in", "Sign in", "Sign In", "Log In"};
        AriaRole[] roles = {AriaRole.BUTTON, AriaRole.LINK, AriaRole.MENUITEM, AriaRole.TAB};
        for (String name : names) {
            for (AriaRole role : roles) {
                try {
                    Locator byRole = page.getByRole(role, new Page.GetByRoleOptions().setName(name).setExact(true));
                    Locator visible = firstVisible(byRole);
                    if (visible != null) {
                        ControlType ct = ControlClassifier.classify(visible);
                        TraceLogger.info("LOCATOR", "LOGIN_EXACT_ROLE", "Resolved auth control via exact role name", TraceMeta.of(
                                "role", role.name(), "name", name
                        ));
                        return new ResolvedElement("role", role.name().toLowerCase(Locale.ROOT) + "|" + name,
                                0.93, false, visible, null, ct, "main", "", "", "", "");
                    }
                } catch (RuntimeException ignored) {
                }
            }
            ResolvedElement leaf = resolveCompactAuthText(page, name);
            if (leaf != null) {
                TraceLogger.info("LOCATOR", "LOGIN_EXACT_TEXT", "Resolved auth control via compact text", TraceMeta.of(
                        "name", name, "locator", leaf.resolvedLocator()
                ));
                return leaf;
            }
        }
        return null;
    }

    private ResolvedElement resolveCompactAuthText(Page page, String name) {
        try {
            Locator matches = page.getByText(name, new Page.GetByTextOptions().setExact(true));
            Locator best = null;
            int bestArea = Integer.MAX_VALUE;
            int count = Math.min(matches.count(), 16);
            for (int i = 0; i < count; i++) {
                Locator candidate = matches.nth(i);
                if (!candidate.isVisible()) {
                    continue;
                }
                Object areaObj = candidate.evaluate("""
                        (el, expected) => {
                          const text = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim();
                          if (text !== expected) return Number.MAX_SAFE_INTEGER;
                          const r = el.getBoundingClientRect();
                          const area = Math.round(r.width * r.height);
                          return area > 0 && area < 80000 ? area : Number.MAX_SAFE_INTEGER;
                        }
                        """, name);
                int area = areaObj instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
                if (area < bestArea) {
                    bestArea = area;
                    best = candidate;
                }
            }
            if (best == null) {
                return null;
            }
            // Keep the exact text leaf handle. Promotion happens at click-time with revert fallback.
            ControlType ct = ControlClassifier.classify(best);
            return new ResolvedElement("text", name, 0.92, false, best, null, ct, "main", "", "", "", "");
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Locator firstVisible(Locator locator) {
        return VisibleLocatorPicker.firstVisible(locator);
    }

    private ResolvedElement findAssociatedControl(
            Page page, String action, String target, ElementCandidate labelCandidate, BrowserSnapshot snapshot) {
        // Strategy 1: Use the pre-computed association from DOM extraction
        if (labelCandidate.hasAssociatedControl()) {
            String selector = labelCandidate.associatedControlSelector();
            try {
                Locator control = page.locator(selector);
                if (isUniqueVisible(control)) {
                    ControlType ct = ControlClassifier.classify(control);
                    if (ActionCompatibility.isCompatible(action, ct)) {
                        TraceLogger.info("LOCATOR", "ASSOCIATED_CONTROL_FOUND",
                                "Found associated control via DOM association", TraceMeta.of(
                                        "label", target, "controlSelector", selector,
                                        "controlType", ct.name()));
                        return uniqueReplayable(page, control, ct, target, 0.90);
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }

        // Strategy 2: Use Playwright's getByLabel which handles for/id and nesting
        try {
            String accessible = stripControlSuffix(target);
            Locator byLabel = page.getByLabel(accessible);
            if (isUniqueVisible(byLabel)) {
                ControlType ct = ControlClassifier.classify(byLabel);
                if (ActionCompatibility.isCompatible(action, ct)) {
                    TraceLogger.info("LOCATOR", "ASSOCIATED_CONTROL_FOUND",
                            "Found associated control via getByLabel", TraceMeta.of(
                                    "label", accessible, "controlType", ct.name()));
                    return uniqueReplayable(page, byLabel, ct, accessible, 0.92);
                }
            }
        } catch (RuntimeException ignored) {
        }

        if ("select".equalsIgnoreCase(action)) {
            ResolvedElement labeledDropdown = findDropdownByLabel(page, target);
            if (labeledDropdown != null) {
                return labeledDropdown;
            }
        }
        if (isFillAction(action)) {
            ResolvedElement labeledInput = findInputByLabel(page, target);
            if (labeledInput != null) {
                return labeledInput;
            }
        }

        // Strategy 3: Find interactive control in the smallest container around the label
        String labelText = stripControlSuffix(target);
        try {
            Locator nearbyControl = page.locator(
                    "xpath=//label[normalize-space(.)='" + escapeXPath(labelText)
                            + "' and not(@role='columnheader')]/following-sibling::*[contains(@class,'select') "
                            + "or @role='combobox' or @role='listbox' or @aria-expanded][1]"
                            + " | //label[normalize-space(.)='" + escapeXPath(labelText)
                            + "' and not(@role='columnheader')]/parent::*/*[contains(@class,'select') "
                            + "or @role='combobox' or @role='listbox' or @aria-expanded][1]"
                            + " | //label[normalize-space(.)='" + escapeXPath(labelText)
                            + "' and not(@role='columnheader')]/parent::*/following-sibling::*[contains(@class,'select') "
                            + "or @role='combobox' or @role='listbox' or @aria-expanded][1]"
                            + " | //*[normalize-space(.)='" + escapeXPath(labelText)
                            + "' and (self::label or self::span or self::div[contains(@class,'label')]) "
                            + "and not(@role='columnheader')]/following-sibling::*[contains(@class,'select') "
                            + "or @role='combobox' or @role='listbox' or @aria-expanded][1]"
                            + " | //label[normalize-space(.)='" + escapeXPath(labelText)
                            + "' and not(@role='columnheader')]/ancestor::*[position()<=3]//select"
                            + " | //label[normalize-space(.)='" + escapeXPath(labelText)
                            + "' and not(@role='columnheader')]/ancestor::*[position()<=3]"
                            + "//*[@role='combobox' or @role='listbox' or @aria-expanded]"
            );
            int count = nearbyControl.count();
            for (int i = 0; i < count; i++) {
                Locator candidate = nearbyControl.nth(i);
                if (candidate.isVisible()) {
                    ControlType ct = ControlClassifier.classify(candidate);
                    if (ct.isInteractive() && ActionCompatibility.isCompatible(action, ct)) {
                        TraceLogger.info("LOCATOR", "ASSOCIATED_CONTROL_FOUND",
                                "Found associated control via DOM proximity search", TraceMeta.of(
                                        "label", target, "controlType", ct.name()));
                        return uniqueReplayable(page, candidate, ct, labelText, 0.85);
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }

        return null;
    }

    private static boolean isFillAction(String action) {
        return "input".equalsIgnoreCase(action)
                || "type".equalsIgnoreCase(action)
                || "fill".equalsIgnoreCase(action)
                || "search".equalsIgnoreCase(action);
    }

    private static boolean shouldSearchAssociatedControl(String action, ElementCandidate element) {
        if (element == null) {
            return false;
        }
        if (!(isFillAction(action) || "select".equalsIgnoreCase(action))) {
            return false;
        }
        return element.isLabel() || element.hasAssociatedControl() || element.isTabularChrome();
    }

    private ResolvedElement findInputByLabel(Page page, String target) {
        String labelText = stripControlSuffix(target);
        if (labelText.isBlank()) {
            return null;
        }
        try {
            Locator byLabel = page.getByLabel(labelText);
            if (isUniqueVisible(byLabel)) {
                ControlType ct = ControlClassifier.classify(byLabel);
                if (ct.supportsInput()) {
                    TraceLogger.info("LOCATOR", "ASSOCIATED_CONTROL_FOUND",
                            "Found input via getByLabel", TraceMeta.of("label", labelText, "controlType", ct.name()));
                    return uniqueReplayable(page, byLabel, ct, labelText, 0.92);
                }
            }
        } catch (RuntimeException ignored) {
        }
        Locator labels = page.locator(
                "xpath=//*[normalize-space(.)='" + escapeXPath(labelText) + "' "
                        + "and (self::label or self::span or self::div or self::p) "
                        + "and not(self::select) and not(self::input) "
                        + "and not(@role='columnheader') and not(@role='rowheader') "
                        + "and string-length(normalize-space(.)) <= " + (labelText.length() + 10) + "]"
        );
        try {
            int labelCount = labels.count();
            for (int i = 0; i < labelCount; i++) {
                Locator label = labels.nth(i);
                if (!label.isVisible()) {
                    continue;
                }
                ResolvedElement fromGroup = findInputInFieldGroup(page, label, labelText);
                if (fromGroup != null) {
                    return fromGroup;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private ResolvedElement findInputInFieldGroup(Page page, Locator label, String labelText) {
        try {
            Locator group = label.locator("xpath=ancestor-or-self::*[contains(@class,'field') "
                    + "or contains(@class,'group') or contains(@class,'grid') "
                    + "or contains(@class,'input')][1]");
            if (group.count() == 0) {
                group = label.locator("xpath=ancestor::*[position()<=3]").first();
            }
            Locator inputs = group.locator("input, textarea, [role='textbox'], [contenteditable='true']");
            for (int i = 0; i < inputs.count(); i++) {
                Locator input = inputs.nth(i);
                if (!input.isVisible() || !input.isEnabled() || isGlobalSearchInput(input)) {
                    continue;
                }
                ControlType ct = ControlClassifier.classify(input);
                if (ct.supportsInput()) {
                    TraceLogger.info("LOCATOR", "ASSOCIATED_CONTROL_FOUND",
                            "Found input in labeled field group", TraceMeta.of(
                                    "label", labelText, "controlType", ct.name()));
                    return uniqueReplayable(page, input, ct, labelText, 0.90);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private ResolvedElement findDropdownByLabel(Page page, String target) {
        String labelText = stripControlSuffix(target);
        Locator labels = page.locator(
                "xpath=//*[normalize-space(.)='" + escapeXPath(labelText) + "' "
                        + "and (self::label or self::span or self::div or self::p) "
                        + "and not(self::select) and not(self::input) "
                        + "and not(@role='columnheader') and not(@role='rowheader') "
                        + "and string-length(normalize-space(.)) <= " + (labelText.length() + 10) + "]"
        );
        int labelCount = labels.count();
        for (int i = 0; i < labelCount; i++) {
            Locator label = labels.nth(i);
            if (!label.isVisible()) {
                continue;
            }
            ResolvedElement fromGroup = findDropdownInFieldGroup(page, label, labelText);
            if (fromGroup != null) {
                return fromGroup;
            }
        }
        return null;
    }

    private ResolvedElement findDropdownInFieldGroup(Page page, Locator label, String labelText) {
        try {
            Locator sibling = label.locator("xpath=following-sibling::*[1]");
            if (sibling.count() > 0) {
                ResolvedElement siblingHit = findDropdownTriggerInContainer(page, sibling, labelText);
                if (siblingHit != null) {
                    return siblingHit;
                }
            }
            Locator parent = label.locator("xpath=..");
            if (parent.count() > 0) {
                ResolvedElement parentHit = findDropdownTriggerInContainer(page, parent, labelText);
                if (parentHit != null) {
                    return parentHit;
                }
            }
            Locator ancestors = label.locator("xpath=ancestor::*[position()<=4]");
            for (int depth = 0; depth < ancestors.count(); depth++) {
                ResolvedElement scopedHit = findDropdownTriggerInContainer(page, ancestors.nth(depth), labelText);
                if (scopedHit != null) {
                    return scopedHit;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private ResolvedElement findDropdownTriggerInContainer(Page page, Locator container, String labelText) {
        try {
            Locator preferred = container.locator(
                    "[aria-expanded], [role='combobox'], [role='listbox'], [role='button'][aria-haspopup]");
            ResolvedElement preferredHit = firstInteractiveTrigger(page, preferred, labelText);
            if (preferredHit != null) {
                return preferredHit;
            }
            Locator fallback = container.locator(
                    "[class*='select']:not(label):not(option):not(input), [class*='dropdown']:not(label)");
            ResolvedElement fallbackHit = firstInteractiveTrigger(page, fallback, labelText);
            if (fallbackHit != null) {
                return fallbackHit;
            }
            for (String placeholder : new String[]{"-- Select --", "Select", "Choose"}) {
                Locator placeholderTrigger = container.getByText(placeholder, new Locator.GetByTextOptions().setExact(true));
                if (placeholderTrigger.count() >= 1 && placeholderTrigger.first().isVisible()) {
                    ControlType ct = ControlClassifier.classify(placeholderTrigger.first());
                    TraceLogger.info("LOCATOR", "LABEL_DROPDOWN_FOUND",
                            "Found dropdown placeholder trigger in field group", TraceMeta.of(
                                    "label", labelText, "placeholder", placeholder));
                    return uniqueReplayable(page, placeholderTrigger.first(), ct, labelText, 0.90);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private ResolvedElement firstInteractiveTrigger(Page page, Locator triggers, String labelText) {
        for (int i = 0; i < triggers.count(); i++) {
            Locator trigger = triggers.nth(i);
            if (!trigger.isVisible() || !trigger.isEnabled()) {
                continue;
            }
            String tag = "";
            try {
                Object raw = trigger.evaluate("el => (el.tagName || '').toLowerCase()");
                tag = raw == null ? "" : raw.toString();
            } catch (RuntimeException ignored) {
            }
            if ("input".equals(tag) || "textarea".equals(tag) || "option".equals(tag)) {
                continue;
            }
            ControlType ct = ControlClassifier.classify(trigger);
            if (ct.isInteractive()) {
                TraceLogger.info("LOCATOR", "LABEL_DROPDOWN_FOUND",
                        "Found dropdown trigger in field group", TraceMeta.of(
                                "label", labelText, "controlType", ct.name()));
                return uniqueReplayable(page, trigger, ct, labelText, 0.91);
            }
        }
        return null;
    }

    /**
     * Persist a locator that is unique when replayed from generated code.
     * The live Playwright Locator may already be scoped; class-only CSS often is not.
     */
    private ResolvedElement uniqueReplayable(
            Page page, Locator live, ControlType ct, String labelText, double confidence) {
        String persistType = "css";
        String persistValue = buildSelectorForLocator(live);
        try {
            if (!isUniqueVisible(page.locator(persistValue), persistValue)) {
                persistValue = uniqueCssPath(live);
            }
        } catch (RuntimeException ex) {
            persistValue = uniqueCssPath(live);
        }

        String label = stripControlSuffix(labelText);
        if (!label.isBlank()) {
            try {
                Locator byLabel = page.getByLabel(label);
                if (isUniqueVisible(byLabel) && sameNode(byLabel, live)) {
                    persistType = "label";
                    persistValue = label;
                    confidence = Math.max(confidence, 0.92);
                }
            } catch (RuntimeException ignored) {
            }
            if (!"label".equals(persistType)) {
                AriaRole[] roles = {AriaRole.COMBOBOX, AriaRole.TEXTBOX, AriaRole.LISTBOX, AriaRole.BUTTON};
                for (AriaRole role : roles) {
                    try {
                        Locator byRole = page.getByRole(role, new Page.GetByRoleOptions().setName(label));
                        if (isUniqueVisible(byRole) && sameNode(byRole, live)) {
                            String roleName = switch (role) {
                                case COMBOBOX -> "combobox";
                                case TEXTBOX -> "textbox";
                                case LISTBOX -> "listbox";
                                case BUTTON -> "button";
                                default -> role.name().toLowerCase();
                            };
                            persistType = "role";
                            persistValue = roleName + "|" + label;
                            confidence = Math.max(confidence, 0.94);
                            break;
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
        TraceLogger.info("LOCATOR", "LOCATOR_SELECTED", "Persisting unique replayable locator", TraceMeta.of(
                "locatorType", persistType,
                "locator", persistValue,
                "controlType", ct.name(),
                "keptLiveLocator", true
        ));
        return new ResolvedElement(persistType, persistValue, confidence, false, live, null, ct,
                "main", "", "", "", "");
    }

    private static boolean sameNode(Locator left, Locator right) {
        try {
            Object same = left.evaluate("(el, other) => el === other", right);
            return Boolean.TRUE.equals(same);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String uniqueCssPath(Locator locator) {
        try {
            Object result = locator.evaluate("""
                    el => {
                      const parts = [];
                      let current = el;
                      let guard = 0;
                      while (current && current.nodeType === 1 && guard < 8) {
                        let part = current.tagName.toLowerCase();
                        if (current.id) {
                          part += '#' + current.id;
                          parts.unshift(part);
                          break;
                        }
                        const parent = current.parentElement;
                        if (parent) {
                          const same = Array.from(parent.children).filter(c => c.tagName === current.tagName);
                          if (same.length > 1) {
                            part += ':nth-of-type(' + (same.indexOf(current) + 1) + ')';
                          }
                        }
                        parts.unshift(part);
                        current = current.parentElement;
                        guard += 1;
                      }
                      return parts.join(' > ');
                    }
                    """);
            return result == null ? "div" : result.toString();
        } catch (RuntimeException ex) {
            return "div";
        }
    }

    private static String stripControlSuffix(String target) {
        if (target == null) return "";
        return target.replaceAll("(?i)\\s+(dropdown|field|input|button|selector|select|textbox|checkbox|radio|picker)$", "").trim();
    }

    private static boolean isGlobalSearchInput(Locator input) {
        try {
            Object result = input.evaluate("""
                    el => {
                      const ph = (el.getAttribute('placeholder') || '').trim().toLowerCase();
                      if (ph === 'search') return true;
                      if (ph.includes('search') && !ph.includes('hint') && !ph.includes('type for')) return true;
                      return false;
                    }
                    """);
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String escapeXPath(String value) {
        return value.replace("'", "\\'");
    }

    private static String buildSelectorForLocator(Locator locator) {
        try {
            Object result = locator.evaluate("""
                    el => {
                      if (el.id) return '#' + el.id;
                      const testId = el.getAttribute('data-testid');
                      if (testId) return '[data-testid="' + testId + '"]';
                      const tag = el.tagName.toLowerCase();
                      const role = el.getAttribute('role');
                      if (role) return tag + '[role="' + role + '"]';
                      const cls = el.className;
                      if (cls && typeof cls === 'string') {
                        const parts = cls.split(' ').filter(c => c.length > 0).slice(0, 2);
                        if (parts.length > 0) return tag + '.' + parts.join('.');
                      }
                      return tag;
                    }
                    """);
            return result == null ? "div" : result.toString();
        } catch (RuntimeException ex) {
            return "div";
        }
    }

    public java.util.Optional<ResolvedElement> verifyKnownLocator(Page page, String previousLocator, String previousType) {
        if (previousLocator == null || previousLocator.isBlank() || previousType == null || previousType.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            Locator previous = toLocator(page, new LocatorCandidateFactory.Candidate(previousType, previousLocator, 1.0, "known"));
            if (isUniqueVisible(previous, previousLocator)) {
                ControlType controlType = ControlClassifier.classify(previous);
                return java.util.Optional.of(new ResolvedElement(previousType, previousLocator, 0.95, false, previous, null, controlType,
                        "main", "", "", "", ""));
            }
        } catch (RuntimeException ignored) {
            // Known locator is no longer valid; caller should resolve or heal.
        }
        return java.util.Optional.empty();
    }

    public ResolvedElement heal(Page page, String action, String target, String previousLocator, String previousType) {
        return heal(page, action, target, previousLocator, previousType, Duration.ofSeconds(30));
    }

    public ResolvedElement heal(
            Page page,
            String action,
            String target,
            String previousLocator,
            String previousType,
            Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        BrowserSnapshot snapshot = intelligence.inspect(page, List.of());
        if (previousLocator != null && previousType != null) {
            try {
                Locator previous = toLocator(page, new LocatorCandidateFactory.Candidate(previousType, previousLocator, 1.0, "known"));
                if (isUniqueVisible(previous)) {
                    return new ResolvedElement(previousType, previousLocator, 0.95, false, previous, null);
                }
            } catch (RuntimeException ignored) {
                // continue with DOM rediscovery
            }
        }
        if (System.nanoTime() > deadline) {
            throw new SmartQaException(ErrorCode.LOCATOR_FAILURE, "Healing timed out for: " + target);
        }
        try {
            return resolve(page, action, target, snapshot).asHealed();
        } catch (SmartQaException ex) {
            throw new SmartQaException(ErrorCode.LOCATOR_FAILURE, "Unable to heal locator for: " + target, ex);
        }
    }

    private ResolvedElement firstVerified(Page page, ElementCandidate element, List<RankedLocator> locators) {
        if (locators == null) {
            return null;
        }
        Frame frame = resolveFrame(page, element == null ? "" : element.iframeContext());
        if (frame == null) {
            return null;
        }
        if (frame.isDetached()) {
            return null;
        }
        for (RankedLocator candidate : locators) {
            if (!LocatorContract.isUsable(candidate.locatorType(), candidate.resolvedLocator())) {
                LocatorContract.Validation rejected = LocatorContract.validate(
                        candidate.locatorType(), candidate.resolvedLocator(), "ElementResolver.firstVerified");
                TraceLogger.warn("LOCATOR", "LOCATOR_INVALID", rejected.reason(), TraceMeta.of(
                        "locatorType", candidate.locatorType() == null ? "" : candidate.locatorType(),
                        "locatorValue", candidate.resolvedLocator() == null ? "" : candidate.resolvedLocator(),
                        "reason", rejected.reason(),
                        "sourceComponent", rejected.sourceComponent(),
                        "originalCandidate", rejected.originalCandidate()
                ));
                continue;
            }
            Locator locator = toLocator(page, frame, new LocatorCandidateFactory.Candidate(
                    candidate.locatorType(), candidate.resolvedLocator(), candidate.confidence(), candidate.reason()));
            if (locator == null) {
                continue;
            }
            if (isUniqueVisible(locator, candidate.resolvedLocator())) {
                return new ResolvedElement(
                        candidate.locatorType(),
                        candidate.resolvedLocator(),
                        candidate.confidence(),
                        false,
                        locator,
                        null,
                        null,
                        element == null ? "main" : element.iframeContext(),
                        element == null ? "" : element.frameUrl(),
                        element == null ? "" : element.frameName(),
                        element == null ? "" : element.parentFrameContext(),
                        element == null ? "" : element.targetPath()
                );
            }
        }
        return null;
    }

    private static boolean ambiguous(List<LocatorRanker.RankedElement> ranked) {
        return ranked.size() >= 2
                && ranked.getFirst().score() >= 80
                && ranked.getFirst().score() - ranked.get(1).score() < 30;
    }

    private static boolean rankedDomContainsVisualValue(
            List<LocatorRanker.RankedElement> ranked, String value) {
        if (ranked == null || ranked.isEmpty() || value == null || value.isBlank()) {
            return false;
        }
        List<String> tokens = distinctiveVisualTokens(value);
        if (tokens.isEmpty()) {
            return false;
        }
        int limit = Math.min(8, ranked.size());
        for (int i = 0; i < limit; i++) {
            String sem = normalizeSem(ranked.get(i).element());
            for (String token : tokens) {
                if (sem.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> distinctiveVisualTokens(String value) {
        java.util.Set<String> stop = java.util.Set.of(
                "click", "tap", "open", "the", "banner", "image", "photo", "icon", "card", "tile",
                "containing", "contains", "shows", "says", "with", "that", "this", "please",
                "button", "link", "visual", "graphic", "picture", "img", "canvas");
        List<String> tokens = new ArrayList<>();
        for (String raw : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() >= 4 && !stop.contains(raw)) {
                tokens.add(raw);
            }
        }
        return tokens;
    }

    private static boolean needsAiBeforeAction(
            List<LocatorRanker.RankedElement> ranked, String action, String target) {
        if (ranked == null || ranked.size() < 2) {
            return false;
        }
        if (LocatorRanker.uniqueWinner(ranked)) {
            return false;
        }
        if (ambiguous(ranked)) {
            return true;
        }
        double top = ranked.getFirst().score();
        double second = ranked.get(1).score();
        double conf = LocatorRanker.confidence(top);
        // Medium confidence band — ask AI before acting
        if (conf >= 0.55 && conf < 0.85 && top - second < 45) {
            return true;
        }
        // Profile/account vs nearby cart/bag icons — always escalate when both score well
        if (isProfileIconHint(action, target) && ranked.size() >= 2) {
            String topSem = normalizeSem(ranked.getFirst().element());
            String secondSem = normalizeSem(ranked.get(1).element());
            boolean topCart = topSem.contains("cart") || topSem.contains("bag") || topSem.contains("basket");
            boolean secondCart = secondSem.contains("cart") || secondSem.contains("bag") || secondSem.contains("basket");
            boolean topProfile = topSem.contains("profile") || topSem.contains("account") || topSem.contains("user")
                    || topSem.contains("avatar");
            boolean secondProfile = secondSem.contains("profile") || secondSem.contains("account")
                    || secondSem.contains("user") || secondSem.contains("avatar");
            if ((topCart || secondCart) && (topProfile || secondProfile || !topCart)) {
                return top - second < 60;
            }
            // Weak aria "Button Double Tap..." vs unlabeled header icon
            if (topSem.contains("double tap") || secondSem.contains("double tap")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProfileIconHint(String action, String target) {
        return "PROFILE".equals(intentPrefix(action, target));
    }

    private static String normalizeSem(ElementCandidate element) {
        if (element == null) {
            return "";
        }
        return String.join(" ",
                nullToEmpty(element.accessibleName()),
                nullToEmpty(element.text()),
                nullToEmpty(element.title()),
                nullToEmpty(element.ariaLabel()),
                nullToEmpty(element.parentContext()),
                nullToEmpty(element.nearbyText())).toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<LocatorRanker.RankedElement> retainOptionMatches(
            List<LocatorRanker.RankedElement> ranked, String optionValue) {
        if (ranked == null || ranked.isEmpty() || optionValue == null || optionValue.isBlank()) {
            return ranked == null ? List.of() : ranked;
        }
        List<LocatorRanker.RankedElement> matched = new ArrayList<>();
        for (LocatorRanker.RankedElement item : ranked) {
            if (LocatorRanker.optionMatches(item.element(), optionValue)) {
                matched.add(item);
            }
        }
        return matched.isEmpty() ? ranked : matched;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
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

    /**
     * AI compares compact multimodal evidence (fresh screenshot + candidates).
     * Safety Gate requires the chosen candidate to still resolve to a unique visible locator on the live page.
     * AI returns candidate index / semantic choice only — never authoritative CSS/XPath.
     */
    private LocatorRanker.RankedElement aiAssistBeforeAction(
            Page page,
            String action,
            String target,
            BrowserSnapshot snapshot,
            List<LocatorRanker.RankedElement> ranked) {
        int limit = Math.min(5, ranked.size());
        List<String> candidateLabels = new ArrayList<>();
        List<Double> candidateScores = new ArrayList<>();
        StringBuilder candidates = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            LocatorRanker.RankedElement item = ranked.get(i);
            ElementCandidate el = item.element();
            String id = "candidate-" + (char) ('A' + i);
            String label = id + " score=" + Math.round(item.score())
                    + " tag=" + el.tag()
                    + " role=" + el.role()
                    + " name=" + el.accessibleName()
                    + " text=" + el.text()
                    + " title=" + el.title()
                    + " aria=" + el.ariaLabel()
                    + " region=" + el.region()
                    + " heading=" + el.headingContext()
                    + " ancestors=" + el.ancestorContext()
                    + " parent=" + el.parentContext()
                    + " siblings=" + el.siblingContext()
                    + " header=" + el.inHeaderRegion()
                    + " quality=" + el.evidenceQuality()
                    + " bbox=" + el.boundingBox();
            candidateLabels.add(label);
            candidateScores.add(item.score());
            candidates.append(i).append(") ").append(label).append('\n');
        }
        byte[] screenshot = captureFreshScreenshot(page);
        String compactDom = snapshot == null ? "" : intelligence.compactForAi(snapshot);
        AiEvidenceBundle bundle = AiEvidenceBundle.forBeforeAction(
                snapshot == null ? "" : snapshot.url(),
                snapshot == null ? "" : snapshot.title(),
                action,
                target,
                action + " " + target,
                candidateLabels,
                candidateScores,
                screenshot,
                compactDom,
                "");
        String system = """
                You help choose ONE interactive candidate for a browser action using screenshot + DOM evidence.
                Return STRICT JSON only:
                {"selectedIndex":0,"recommendedCandidateId":"candidate-A","confidence":0.0,"reason":"...","reject":false}
                selectedIndex must be one of the listed indices (0-based).
                Prefer semantic + visual match to the instruction (e.g. profile/account over cart/bag).
                For icon-only header controls, use the screenshot when DOM labels are weak.
                Never invent CSS/XPath. Never weaken the user goal. Never use nth()/coordinates as truth.
                """;
        String user = "Action: " + action + "\nTarget: " + target + "\n"
                + bundle.toCompactText()
                + "\nIndexed candidates:\n" + candidates;
        AiPrompt prompt = AiPrompt.json(system, user, bundle.mediaParts());
        long started = System.currentTimeMillis();
        String providerId = aiProvider.id();
        AiTelemetry.callStarted(
                "ambiguous_or_medium_confidence",
                providerId,
                "",
                bundle.evidenceSize(),
                bundle.screenshotIncluded(),
                bundle.domIncluded());
        try {
            int timeoutSec = Math.max(90, com.smartqa.ai.AiCalls.timeoutSeconds(properties));
            String json = com.smartqa.ai.AiCalls.awaitText(aiProvider, prompt, timeoutSec);
            JsonNode node = objectMapper.readTree(JsonSupport.extractJson(json));
            if (node.path("reject").asBoolean(false)) {
                AiTelemetry.callCompleted(
                        "ambiguous_or_medium_confidence", providerId, "",
                        bundle.evidenceSize(), bundle.screenshotIncluded(), bundle.domIncluded(),
                        System.currentTimeMillis() - started, "AMBIGUOUS_ELEMENT", 0,
                        "", false, "rejected");
                return null;
            }
            int index = node.path("selectedIndex").asInt(-1);
            if (index < 0) {
                index = candidateIdToIndex(node.path("recommendedCandidateId").asText(""), limit);
            }
            double confidence = node.path("confidence").asDouble(0);
            String reason = node.path("reason").asText("");
            if (index < 0 || index >= limit || confidence < 0.45) {
                AiTelemetry.callCompleted(
                        "ambiguous_or_medium_confidence", providerId, "",
                        bundle.evidenceSize(), bundle.screenshotIncluded(), bundle.domIncluded(),
                        System.currentTimeMillis() - started, "AMBIGUOUS_ELEMENT", confidence,
                        "SELECT_CANDIDATE_" + index, false, "rejected");
                return null;
            }
            AiTelemetry.callCompleted(
                    "ambiguous_or_medium_confidence", providerId, "",
                    bundle.evidenceSize(), bundle.screenshotIncluded(), bundle.domIncluded(),
                    System.currentTimeMillis() - started, "AMBIGUOUS_ELEMENT", confidence,
                    "SELECT_CANDIDATE_" + index,
                    true,
                    "diagnosis_ready");
            TraceLogger.info("AI", "AI_BEFORE_ACTION_REASON", "AI candidate reasoning", TraceMeta.of(
                    "selectedIndex", index,
                    "reason", reason.length() > 160 ? reason.substring(0, 160) : reason,
                    "screenshotIncluded", bundle.screenshotIncluded()
            ));
            LocatorRanker.RankedElement selected = ranked.get(index);
            // Multimodal AI is authoritative for profile vs cart; rightmost heuristic only if AI unavailable.
            return selected;
        } catch (RuntimeException ex) {
            AiTelemetry.callCompleted(
                    "ambiguous_or_medium_confidence", providerId, "",
                    bundle.evidenceSize(), bundle.screenshotIncluded(), bundle.domIncluded(),
                    System.currentTimeMillis() - started, "AMBIGUOUS_ELEMENT", 0,
                    "", false, "ai_unavailable");
            TraceLogger.warn("AI", "AI_CALL_FALLBACK", "AI before-action unavailable", TraceMeta.of(
                    "latencyMs", System.currentTimeMillis() - started,
                    "message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    "finalOutcome", "ai_unavailable"
            ));
            return null;
        }
    }

    private static int candidateIdToIndex(String candidateId, int limit) {
        if (candidateId == null || candidateId.isBlank()) {
            return -1;
        }
        String id = candidateId.trim().toUpperCase(Locale.ROOT);
        if (id.startsWith("CANDIDATE-") && id.length() >= 11) {
            char letter = id.charAt(10);
            int index = letter - 'A';
            if (index >= 0 && index < limit) {
                return index;
            }
        }
        return -1;
    }

    private static byte[] captureFreshScreenshot(Page page) {
        if (page == null) {
            return new byte[0];
        }
        try {
            return page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
        } catch (RuntimeException ex) {
            return new byte[0];
        }
    }

    private static String intentPrefix(String action, String target) {
        if (!"click".equalsIgnoreCase(action) || target == null) {
            return null;
        }
        String hint = target.toLowerCase(Locale.ROOT).trim();
        if (hint.contains("profile") || hint.contains("account") || hint.contains("user")) {
            return "PROFILE";
        }
        if (hint.equals("login") || hint.equals("log in") || hint.equals("sign in") || hint.equals("signin")) {
            return "LOGIN";
        }
        return null;
    }

    private static void emitIntentCandidateTrace(String action, String target, List<LocatorRanker.RankedElement> ranked) {
        String prefix = intentPrefix(action, target);
        if (prefix == null) {
            return;
        }
        TraceLogger.info("LOCATOR", prefix + "_CANDIDATES_DISCOVERED", prefix + " candidates ranked", TraceMeta.of(
                "target", target,
                "count", ranked.size(),
                "candidates", candidateSummaries(ranked, 12)
        ));
    }

    private static ResolvedElement emitIntentSelected(ResolvedElement resolved, String action, String target) {
        String prefix = intentPrefix(action, target);
        if (prefix == null || resolved == null) {
            return resolved;
        }
        TraceLogger.info("LOCATOR", prefix + "_LOCATOR_SELECTED", prefix + " locator selected: "
                        + resolved.locatorType() + "=" + resolved.resolvedLocator(), TraceMeta.of(
                "target", target,
                "locator", resolved.resolvedLocator(),
                "locatorType", resolved.locatorType(),
                "controlType", resolved.controlType() == null ? "UNKNOWN" : resolved.controlType().name(),
                "confidence", resolved.confidence()
        ));
        return resolved;
    }

    private static String cloud(List<RankedLocator> locators) {
        if (locators == null || locators.isEmpty()) {
            return null;
        }
        return locators.stream()
                .limit(5)
                .map(item -> item.locatorType() + ":" + item.resolvedLocator() + "@" + item.confidence())
                .collect(Collectors.joining(" | "));
    }

    private ResolvedElement singleInteractiveControl(Page page, String action, String target) {
        String lower = action == null ? "" : action.toLowerCase();
        if (!("click".equals(lower) || "hover".equals(lower))) {
            return null;
        }
        Locator links = page.getByRole(AriaRole.LINK);
        if (links.count() == 1 && isUniqueVisible(links)) {
            String text = safeInnerText(links);
            return new ResolvedElement("role", "link|" + (text.isBlank() ? target : text), 0.62, false, links, null);
        }
        Locator buttons = page.getByRole(AriaRole.BUTTON);
        if (buttons.count() == 1 && isUniqueVisible(buttons)) {
            String text = safeInnerText(buttons);
            return new ResolvedElement("role", "button|" + (text.isBlank() ? target : text), 0.62, false, buttons, null);
        }
        return null;
    }

    private static String safeInnerText(Locator locator) {
        try {
            String text = locator.innerText(new Locator.InnerTextOptions().setTimeout(1000));
            return text == null ? "" : text.trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private ResolvedElement multimodalResolve(
            Page page,
            String action,
            String target,
            BrowserSnapshot snapshot,
            List<LocatorRanker.RankedElement> ranked,
            String trigger) {
        if (multimodal == null || page == null) {
            TraceLogger.warn("AI", "MULTIMODAL_UNAVAILABLE", "Multimodal engine is not wired", TraceMeta.of(
                    "target", target,
                    "trigger", trigger
            ));
            return null;
        }
        TraceLogger.info("AI", "MULTIMODAL_ESCALATE", "Escalating to multimodal target discovery", TraceMeta.of(
                "action", action,
                "target", target,
                "trigger", trigger,
                "url", page.url(),
                "rankedCount", ranked == null ? 0 : ranked.size()
        ));
        try {
            var outcome = multimodal.discover(page, action, target, snapshot, ranked, trigger, List.of());
            TraceLogger.info("AI", "MULTIMODAL_RESULT", "Multimodal discovery returned", TraceMeta.of(
                    "trigger", trigger,
                    "accepted", outcome.accepted(),
                    "classification", outcome.hypothesis() == null ? "" : outcome.hypothesis().classification(),
                    "confidence", outcome.hypothesis() == null ? 0 : outcome.hypothesis().confidence(),
                    "visualTargetPresent", outcome.hypothesis() != null && outcome.hypothesis().visualTargetPresent(),
                    "screenshotIncluded", outcome.evidence() != null && outcome.evidence().screenshotIncluded(),
                    "recommendedCandidateId", outcome.hypothesis() == null ? "" : outcome.hypothesis().recommendedCandidateId()
            ));
            if (!outcome.accepted() || outcome.ranked().isEmpty()) {
                TraceLogger.warn("AI", "MULTIMODAL_NOT_ACCEPTED", "Multimodal hypothesis was not accepted", TraceMeta.of(
                        "trigger", trigger,
                        "target", target,
                        "classification", outcome.hypothesis() == null ? "" : outcome.hypothesis().classification()
                ));
                return null;
            }
            LocatorRanker.RankedElement preferred = outcome.ranked().orElse(null);
            if (preferred == null) {
                return null;
            }
            ResolvedElement verified = firstVerified(page, preferred.element(), preferred.locators());
            TraceLogger.info("AI", "MULTIMODAL_MAPPED_TO_LIVE_LOCATOR", "Visual target mapped to a live locator", TraceMeta.of(
                    "target", target,
                    "locator", verified == null ? "" : verified.resolvedLocator(),
                    "candidateId", preferred.element() == null ? "" : preferred.element().candidateId()
            ));
            return verified;
        } catch (RuntimeException ex) {
            TraceLogger.warn("AI", "MULTIMODAL_RESOLVE_FAILED", "Multimodal resolve failed", TraceMeta.of(
                    "target", target,
                    "trigger", trigger,
                    "message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
            ));
            return null;
        }
    }

    private ResolvedElement aiFallback(Page page, String action, String target, BrowserSnapshot snapshot) {
        // Last-resort text-only path. Must still match a unique visible live locator; never invent CSS.
        try {
            String compact = intelligence.compactForAi(snapshot);
            String json = com.smartqa.ai.AiCalls.awaitText(aiProvider, AiPrompt.json(
                    "Return JSON {\"decision\":\"USE_EXISTING_CANDIDATE\",\"candidateId\":\"...\"}. Select only from live candidateIds. Never invent CSS/XPath.",
                    "Action: " + action + "\nTarget: " + target + "\nCompact interactive DOM:\n" + compact
            ), 60);
            JsonNode node = objectMapper.readTree(JsonSupport.extractJson(json));
            String candidateId = node.path("candidateId").asText();
            if (candidateId.isBlank() || snapshot == null || snapshot.elements() == null) {
                return null;
            }
            ElementCandidate live = snapshot.elements().stream()
                    .filter(el -> candidateId.equals(el.candidateId()))
                    .findFirst()
                    .orElse(null);
            if (live == null) {
                return null;
            }
            List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(live), action, target);
            if (ranked.isEmpty()) {
                return null;
            }
            return firstVerified(page, ranked.getFirst().element(), ranked.getFirst().locators());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Locator toLocator(Page page, LocatorCandidateFactory.Candidate candidate) {
        return toLocator(page, page.mainFrame(), candidate);
    }

    private Locator toLocator(Page page, Frame frame, LocatorCandidateFactory.Candidate candidate) {
        if (candidate == null) {
            throw new SmartQaException(ErrorCode.LOCATOR_INVALID, "LOCATOR_INVALID: missing locator candidate | source=ElementResolver");
        }
        LocatorContract.requireValid(candidate.locatorType(), candidate.resolvedLocator(), "ElementResolver.toLocator");
        Frame owner = frame == null ? page.mainFrame() : frame;
        return switch (candidate.locatorType() == null ? "css" : candidate.locatorType()) {
            case "role" -> roleLocator(owner, candidate.resolvedLocator());
            case "label" -> owner.getByLabel(candidate.resolvedLocator());
            case "placeholder" -> owner.getByPlaceholder(candidate.resolvedLocator());
            case "text" -> owner.getByText(candidate.resolvedLocator());
            default -> owner.locator(candidate.resolvedLocator());
        };
    }

    private static boolean isUsableLocatorSpec(String type, String value) {
        return LocatorContract.isUsable(type, value);
    }

    private Locator roleLocator(Frame frame, String spec) {
        String[] parts = spec.split("\\|", 2);
        String role = parts[0];
        String name = parts.length > 1 ? parts[1] : "";
        AriaRole ariaRole = switch (role) {
            case "button" -> AriaRole.BUTTON;
            case "textbox" -> AriaRole.TEXTBOX;
            case "searchbox" -> AriaRole.SEARCHBOX;
            case "combobox" -> AriaRole.COMBOBOX;
            case "checkbox" -> AriaRole.CHECKBOX;
            case "radio" -> AriaRole.RADIO;
            case "heading" -> AriaRole.HEADING;
            default -> AriaRole.LINK;
        };
        return frame.getByRole(ariaRole, new Frame.GetByRoleOptions().setName(name));
    }

    private Frame resolveFrame(Page page, String frameContext) {
        if (frameContext == null || frameContext.isBlank() || "main".equals(frameContext)) {
            return page.mainFrame();
        }
        Map<Frame, String> paths = new HashMap<>();
        Frame main = page.mainFrame();
        paths.put(main, "main");
        annotateChildren(main, "main", paths);
        for (Map.Entry<Frame, String> entry : paths.entrySet()) {
            if (frameContext.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void annotateChildren(Frame parent, String parentPath, Map<Frame, String> paths) {
        List<Frame> children = parent.childFrames();
        for (int i = 0; i < children.size(); i++) {
            Frame child = children.get(i);
            String childPath = parentPath + "/" + i;
            paths.put(child, childPath);
            annotateChildren(child, childPath, paths);
        }
    }

    private boolean matchesTarget(Locator locator, String target) {
        if (target == null || target.isBlank()) {
            return true;
        }
        String text = "";
        try {
            text = locator.innerText(new Locator.InnerTextOptions().setTimeout(1000));
        } catch (RuntimeException ignored) {
            return true;
        }
        String normalizedTarget = target.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        String normalizedText = text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        for (String token : normalizedTarget.split(" ")) {
            if (token.length() < 4) {
                continue;
            }
            if ("button".equals(token) || "link".equals(token) || "field".equals(token) || "heading".equals(token)) {
                continue;
            }
            if (normalizedText.contains(token)) {
                return true;
            }
        }
        return normalizedText.isBlank();
    }

    private ResolvedElement resolveExactTextLeaf(Page page, String expectedText) {
        if (expectedText == null || expectedText.isBlank()) {
            return null;
        }
        try {
            Locator matches = page.getByText(expectedText, new Page.GetByTextOptions().setExact(true));
            int count = matches.count();
            Locator best = null;
            int bestArea = Integer.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                Locator candidate = matches.nth(i);
                if (!candidate.isVisible() || !candidate.isEnabled()) {
                    continue;
                }
                Object areaObj = candidate.evaluate("""
                        (el, expected) => {
                          const text = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim();
                          if (text !== expected) return Number.MAX_SAFE_INTEGER;
                          const style = window.getComputedStyle(el);
                          const clickable = style.cursor === 'pointer'
                            || el.tagName === 'BUTTON'
                            || el.tagName === 'A'
                            || el.getAttribute('role') === 'button'
                            || el.getAttribute('role') === 'menuitem'
                            || (el.tabIndex >= 0);
                          if (!clickable) return Number.MAX_SAFE_INTEGER;
                          const r = el.getBoundingClientRect();
                          return Math.round(r.width * r.height);
                        }
                        """, expectedText);
                int area = areaObj instanceof Number number ? number.intValue() : Integer.MAX_VALUE;
                if (area < bestArea && area < 80_000) {
                    bestArea = area;
                    best = candidate;
                }
            }
            if (best == null) {
                return null;
            }
            ControlType ct = ControlClassifier.classify(best);
            String persist = uniqueCssPath(best);
            TraceLogger.info("LOCATOR", "LOCATOR_SELECTED", "Selected compact exact-text leaf", TraceMeta.of(
                    "target", expectedText,
                    "locator", persist,
                    "area", bestArea
            ));
            ResolvedElement resolved = new ResolvedElement("css", persist, 0.88, false, best, null, ct, "main", "", "", "", "");
            return emitIntentSelected(resolved, "click", expectedText);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isUniqueVisible(Locator locator) {
        return isUniqueVisible(locator, null);
    }

    private boolean isUniqueVisible(Locator locator, String locatorText) {
        if (locatorText != null) {
            TraceLogger.info("LOCATOR", "LOCATOR_VERIFY_STARTED", "Verifying locator", TraceMeta.of("locator", locatorText));
        }
        try {
            int count = locator.count();
            boolean unique = count == 1;
            // Never call isVisible() on a multi-match locator — Playwright strict mode throws.
            boolean visible = unique && locator.isVisible();
            boolean enabled = visible && locator.isEnabled();
            boolean verified = unique && visible && enabled;
            if (locatorText != null) {
                TraceLogger.info("LOCATOR", verified ? "LOCATOR_VERIFY_RESULT" : "LOCATOR_VERIFY_FAILED",
                        verified ? "Locator verified" : "Locator verification failed",
                        TraceMeta.of(
                                "locator", locatorText,
                                "matches", count,
                                "visible", visible,
                                "enabled", enabled,
                                "unique", unique,
                                "verified", verified
                        ));
            }
            return verified;
        } catch (RuntimeException ex) {
            if (locatorText != null) {
                TraceLogger.warn("LOCATOR", "LOCATOR_VERIFY_FAILED", ex.getMessage(), TraceMeta.of(
                        "locator", locatorText,
                        "matches", 0
                ));
            }
            return false;
        }
    }

    private static List<java.util.Map<String, Object>> candidateSummaries(List<LocatorRanker.RankedElement> ranked) {
        return candidateSummaries(ranked, 5);
    }

    private static List<java.util.Map<String, Object>> candidateSummaries(List<LocatorRanker.RankedElement> ranked, int max) {
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        int limit = Math.min(max, ranked.size());
        for (int i = 0; i < limit; i++) {
            LocatorRanker.RankedElement rankedElement = ranked.get(i);
            var element = rankedElement.element();
            double confidence = LocatorRanker.confidence(rankedElement.score());
            out.add(TraceMeta.of(
                    "index", i + 1,
                    "tag", element.tag(),
                    "role", element.role(),
                    "accessibleName", element.accessibleName(),
                    "ariaLabel", element.ariaLabel(),
                    "title", element.title(),
                    "text", element.text(),
                    "id", element.id(),
                    "className", element.className(),
                    "hasIcon", element.hasIcon(),
                    "clickable", element.clickable(),
                    "actionableSelector", element.actionableSelector(),
                    "actionableTag", element.actionableTag(),
                    "actionableRole", element.actionableRole(),
                    "visible", element.visible(),
                    "enabled", element.enabled(),
                    "boundingBox", element.boundingBox(),
                    "inHeaderRegion", element.inHeaderRegion(),
                    "score", rankedElement.score(),
                    "confidence", Math.round(confidence * 100.0) / 100.0,
                    "frameContext", element.iframeContext(),
                    "frameUrl", element.frameUrl(),
                    "frameName", element.frameName(),
                    "parentFrame", element.parentFrameContext(),
                    "targetPath", element.targetPath(),
                    "shadowContext", element.shadowContext()
            ));
        }
        return out;
    }

    private double advisoryHistoryScore(BrowserSnapshot snapshot, String action, String target) {
        if (executionMemory == null || snapshot == null) {
            return 0;
        }
        List<com.smartqa.browser.intelligence.memory.ExecutionMemoryRecord> hints =
                executionMemory.hints(snapshot.url(), action, target);
        if (hints.isEmpty()) {
            return 0;
        }
        return Math.min(30, hints.getFirst().confidence() * 25);
    }

    private ResolvedElement resolveAmbiguousByClarification(
            Page page,
            String action,
            String target,
            List<LocatorRanker.RankedElement> ranked,
            BrowserSnapshot snapshot
    ) {
        List<Map<String, Object>> candidates = clarifications == null
                ? List.of()
                : clarifications.candidatePayloads(ranked, target);
        if (candidates.isEmpty()) {
            candidates = ranked.stream().limit(4).map(rankedElement -> {
                Map<String, Object> row = new HashMap<>();
                row.put("candidateId", rankedElement.element().candidateId());
                row.put("label", rankedElement.element().accessibleName());
                row.put("score", rankedElement.score());
                return row;
            }).toList();
        }
        TraceLogger.warn("LOCATOR", "TARGET_AMBIGUOUS",
                "Multiple equally supported candidates; waiting for clarification", TraceMeta.of(
                        "target", target,
                        "candidates", candidates.size()
                ));
        if (clarifications == null) {
            throw new ClarificationRequiredException(
                    null,
                    "TARGET_AMBIGUOUS: multiple equally supported matches for '" + target + "'",
                    candidates
            );
        }
        RuntimeClarificationService.RuntimeClarification paused = clarifications.pause(
                RuntimeExecutionContext.testCaseId(),
                RuntimeExecutionContext.executionRunId(),
                RuntimeExecutionContext.stepId(),
                target,
                RuntimeClarificationService.TARGET_AMBIGUOUS,
                candidates
        );
        RuntimeClarificationService.RuntimeClarification resolved =
                clarifications.await(paused.id(), Duration.ofMinutes(15));
        if (resolved == null || !"RESOLVED".equals(resolved.status()) || resolved.selectedCandidateId() == null) {
            throw new ClarificationRequiredException(
                    paused.id(),
                    "WAITING_FOR_CLARIFICATION: choose one live candidate for '" + target + "'",
                    candidates
            );
        }
        BrowserSnapshot fresh = page == null || intelligence == null ? snapshot : intelligence.inspect(page, List.of());
        List<LocatorRanker.RankedElement> freshRanked = LocatorRanker.rank(
                fresh == null ? List.of() : fresh.elements(), action, target);
        LocatorRanker.RankedElement selected = findSelected(freshRanked, resolved.selectedCandidateId());
        if (selected == null) {
            throw new SmartQaException(
                    ErrorCode.ELEMENT_NOT_FOUND,
                    "Selected candidate is no longer present after fresh recapture"
            );
        }
        ResolvedElement verified = firstVerified(page, selected.element(), selected.locators());
        if (verified == null) {
            throw new SmartQaException(ErrorCode.ELEMENT_NOT_FOUND, "Selected candidate failed live verification");
        }
        ControlType controlType = ControlClassifier.classify(verified.locator());
        if (!(ActionCompatibility.isCompatible(action, controlType) || controlType == ControlType.OTHER)) {
            throw new SmartQaException(ErrorCode.ACTIONABILITY_FAILURE, "Selected candidate failed Safety Gate");
        }
        return emitIntentSelected(verified.withControlType(controlType), action, target);
    }

    private static LocatorRanker.RankedElement findSelected(
            List<LocatorRanker.RankedElement> ranked, String selectedCandidateId) {
        if (ranked == null || selectedCandidateId == null || selectedCandidateId.isBlank()) {
            return null;
        }
        for (LocatorRanker.RankedElement rankedElement : ranked) {
            String id = rankedElement.element().candidateId();
            String label = rankedElement.element().accessibleName();
            if (selectedCandidateId.equals(id) || selectedCandidateId.equals(label)) {
                return rankedElement;
            }
        }
        return null;
    }

    public record ResolvedElement(
            String locatorType,
            String resolvedLocator,
            double confidence,
            boolean healed,
            Locator locator,
            String locatorCloud,
            ControlType controlType,
            String frameContext,
            String frameUrl,
            String frameName,
            String parentFrameContext,
            String targetPath
    ) {
        ResolvedElement(String locatorType, String resolvedLocator, double confidence,
                        boolean healed, Locator locator, String locatorCloud) {
            this(locatorType, resolvedLocator, confidence, healed, locator, locatorCloud,
                    null, "main", "", "", "", "");
        }

        ResolvedElement withCloud(String cloud) {
            return new ResolvedElement(locatorType, resolvedLocator, confidence, healed, locator, cloud, controlType,
                    frameContext, frameUrl, frameName, parentFrameContext, targetPath);
        }

        ResolvedElement withControlType(ControlType ct) {
            return new ResolvedElement(locatorType, resolvedLocator, confidence, healed, locator, locatorCloud, ct,
                    frameContext, frameUrl, frameName, parentFrameContext, targetPath);
        }

        ResolvedElement asHealed() {
            return new ResolvedElement(locatorType, resolvedLocator, confidence, true, locator, locatorCloud, controlType,
                    frameContext, frameUrl, frameName, parentFrameContext, targetPath);
        }
    }
}
