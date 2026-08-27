package com.smartqa.pipeline;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Classifies pipeline failures into bounded responsibility buckets.
 * Used for auto-retry decisions and as the deterministic fast path before AI.
 */
@Component
public class FailureDiagnostician {

    public FailureDiagnosis diagnose(String stage, String message, int attempt, int maxAttempts) {
        String text = message == null ? "" : message;
        String lower = text.toLowerCase(Locale.ROOT);
        String category;
        String component;
        String why;
        String action;
        boolean healable;

        if (containsAny(lower, "brand &", "compound control", "intent_normalization", "semanticfield", "& model")) {
            category = "INTENT_NORMALIZATION_FAILURE";
            component = "Intent Compiler";
            why = "Action-specific semantic normalization split a multi-word control name into field/value.";
            action = "Repair atomic target, recompile the step, then resolve against fresh evidence. Do not start locator healing yet.";
            healable = true;
        } else if (containsAny(lower, "ai_response_invalid", "structured output", "start_object", "cannot deserialize")) {
            category = "AI_RESPONSE_INVALID";
            component = "AI Structured Output";
            why = "Provider JSON did not match the declared schema.";
            action = "Validate schema, retry the same provider once, then fall back. Do not cooldown the key.";
            healable = true;
        } else if (containsAny(lower, "ollama", "http 400") && containsAny(lower, "400", "format", "/api/chat")) {
            category = "OLLAMA_REQUEST_FAILURE";
            component = "Ollama Provider";
            why = "Ollama rejected the chat payload.";
            action = "Fix model/format/options contract and retry. Do not disable Ollama.";
            healable = true;
        } else if (containsAny(lower, "pool exhausted", "all gemini", "no healthy key")) {
            category = "AI_POOL_EXHAUSTED";
            component = "Gemini Key Pool";
            why = "No healthy Gemini key remained after rotation.";
            action = "Inspect cooldown vs schema failures; restore healthy keys.";
            healable = false;
        } else if (isFilterTargetResolution(lower, text)) {
            category = "FILTER_TARGET_RESOLUTION";
            component = "Filter Intelligence Engine";
            why = "Filter option could not be resolved under the owning filter container; chrome/header matches are not valid.";
            action = "Rediscover the filter container, rank owned options, and retry. Do not treat chrome text as the option.";
            healable = true;
        } else if (containsAny(lower, "filter_container", "filter container", "unable to discover filter")) {
            category = "FILTER_CONTAINER_RESOLUTION";
            component = "Filter Intelligence Engine";
            why = "The filter field container was not located on the live page.";
            action = "Expand collapsed filter sections, re-inspect frames/shadow, and rediscover the container.";
            healable = true;
        } else if (containsAny(lower, "visual_target", "image_text_target", "image_target", "banner", "visual card")
                && containsAny(lower, "not found", "unresolved", "not present", "not clickable")) {
            category = "VISUAL_TARGET_RESOLUTION";
            component = "Multimodal Target Discovery";
            why = "The target appears visual (image/banner/card) and was not mapped to an actionable live candidate.";
            action = "Reconcile screenshot region with live DOM/layout and retry through the Safety Gate.";
            healable = true;
        } else if (containsAny(lower, "wrong_page_state", "wrong page state", "previous page", "go back", "backtrack")) {
            category = "WRONG_PAGE_STATE";
            component = "Recovery Planner";
            why = "The current page cannot satisfy the instruction; a previous browser state may own the target.";
            action = "Diagnose history, backtrack if justified, re-inspect the live page, then resolve.";
            healable = true;
        } else if (containsAny(lower, "multiple matching", "multiple matching", "ambiguous element")
                && !containsAny(lower, "which one", "clarification")) {
            category = "TARGET_AMBIGUOUS";
            component = "Locator Decision Engine";
            why = "Multiple live candidates matched the target; this is a resolution failure, not a bad user instruction.";
            action = "Re-rank with ownership, region, and role evidence; escalate to multimodal if still tied.";
            healable = true;
        } else if (containsAny(lower, "clarification", "which one", "needs_clarification", "needs clarification")
                || (lower.contains("ambiguous") && containsAny(lower, "which ", "choose", "clarify"))) {
            category = "USER_INSTRUCTION";
            component = "Test Understanding Agent";
            why = "The user's actual instruction is genuinely ambiguous or incomplete and requires a choice.";
            action = "Ask the user to choose the intended element. Do not use this category for resolver misses.";
            healable = false;
        } else if (containsAny(lower, "business_state_mismatch", "business assertion", "different business message")) {
            category = "BUSINESS_STATE_MISMATCH";
            component = "Assertion Truth Engine";
            why = "The live application produced a different business result than the requested assertion.";
            action = "Report expected vs actual to the user. Do not rewrite the assertion.";
            healable = false;
        } else if (containsAny(lower, "login_state_failure", "still on the login", "url remains")) {
            category = "LOGIN_STATE_FAILURE";
            component = "Auth/Navigation state";
            why = "Login click completed but authenticated application state was not reached.";
            action = "Re-fill credentials, submit login, wait for post-login URL/state.";
            healable = true;
        } else if (containsAny(lower, "export.", "host_mismatch", "wrong host", "wrong_host", "different host", "wrong_page")) {
            category = "WRONG_HOST";
            component = "Search/Navigation state";
            why = "Browser ended on a different application host than the requested flow.";
            action = "Restore expected host, rediscover search state, keep assertion unchanged.";
            healable = true;
        } else if (containsAny(lower, "locator-like", "implementation details. provide semantic", "intent_validation")) {
            category = "INTENT_VALIDATION";
            component = "Intent Validator";
            why = "Intent failed semantic validation before any browser execution.";
            action = "Normalize URL navigation steps; keep locator-safety. Do not open a browser.";
            healable = false;
        } else if (containsAny(lower, "step needs", "press_key", "intent", "unsupported action", "intent contract")) {
            category = "AI_INTENT";
            component = "Test Understanding Agent";
            why = "Normalized intent contract was incomplete or invalid.";
            action = "Normalize malformed AI intent fields and retry understand.";
            healable = true;
        } else if (containsAny(lower, "filter", "related categor", "chip", "accordion")) {
            category = "FILTER";
            component = "Filter Intelligence Engine";
            why = "Filter control discovery or selection did not reach the requested option.";
            action = "Retry with expanded filter panels and fresh DOM rediscovery.";
            healable = true;
        } else if (containsAny(lower, "search", "autocomplete", "suggestion")) {
            category = "SEARCH";
            component = "Search Intelligence Engine";
            why = "Search input, suggestion, or result selection did not complete.";
            action = "Retry search with fresh DOM after typing and suggestion wait.";
            healable = true;
        } else if (containsAny(lower, "auth entry", "did not expose login", "login/sign in")) {
            category = "ACTIONABILITY";
            component = "Recovery/Healing Engine";
            why = "Auth entry click did not surface a Login/Sign in control in the live browser.";
            action = "Rediscover header auth entry candidates, prefer rightmost unlabeled profile icon over cart, retry.";
            healable = true;
        } else if (containsAny(lower, "locator_invalid", "invalid locator", "malformed locator", "malformed selector", "locator spec rejected")) {
            category = "LOCATOR";
            component = "Locator Decision Engine";
            why = "Locator payload failed contract validation before Playwright execution.";
            action = "Fix candidate construction / locator builder; do not force-click invalid selectors.";
            healable = true;
        } else if (containsAny(lower, "actionability", "element does not exist", "element not visible", "element not enabled", "covered by overlay")) {
            category = "ACTIONABILITY";
            component = "Recovery/Healing Engine";
            why = "Resolved locator was not actionable in the live browser.";
            action = "Refresh DOM, rediscover candidates, dismiss overlays, and retry.";
            healable = true;
        } else if (containsAny(lower, "overlay", "covered", "intercept", "pointer events")) {
            category = "ACTIONABILITY";
            component = "Recovery/Healing Engine";
            why = "Target was not actionable due to overlay or covering element.";
            action = "Dismiss overlay safely, refresh DOM, and retry.";
            healable = true;
        } else if (containsAny(lower, "stale", "detached", "not attached")) {
            category = "DOM_DISCOVERY";
            component = "Element Discovery Engine";
            why = "DOM became stale before the action completed.";
            action = "Refresh DOM, rediscover candidates, and retry.";
            healable = true;
        } else if (containsAny(lower, "assertion failed", "assert", "verify", "expected text", "did not match", "was not found on page", "passwords do not match")) {
            category = "ASSERTION";
            component = "Assertion Engine";
            why = "Expected application state was not observed after actions.";
            action = "Inspect evidence; do not weaken assertion semantics.";
            healable = false;
        } else if (containsAny(lower, "unable to heal", "unable to resolve", "locator", "element not found", "no candidate")) {
            category = "LOCATOR";
            component = "Locator Decision Engine";
            why = "No stable actionable locator matched the requested target.";
            action = "Rediscover candidates with semantic ranking and retry.";
            healable = true;
        } else if (containsAny(lower, "quality gate", "compile", "syntax")) {
            category = "GENERATED_TEST";
            component = "Test Generator";
            why = "Generated Playwright code failed static quality checks.";
            action = "Regenerate code from evidence-backed locators.";
            healable = true;
        } else if (containsAny(lower, "validation", "isolated", "child jvm")) {
            category = "VALIDATOR";
            component = "Independent Validator";
            why = "Independent validation of generated test did not pass.";
            action = "Diagnose failed assertion/step and regenerate if locator-related.";
            healable = true;
        } else if (containsAny(lower, "gemini", "ollama", "ai provider", "timeout", "api key")) {
            category = "ENVIRONMENT";
            component = "AI Provider";
            why = "AI provider was unavailable, timed out, or misconfigured.";
            action = "Check GEMINI_API_KEY / Ollama health, then retry.";
            healable = false;
        } else if (containsAny(lower, "browser", "playwright", "chromium")) {
            category = "BROWSER";
            component = "Browser Observation Agent";
            why = "Browser capability or session failed.";
            action = "Verify Playwright browsers and restart the pipeline.";
            healable = false;
        } else {
            category = mapStageCategory(stage);
            component = mapStageComponent(stage);
            why = "Stage '" + stage + "' failed with an unclassified error.";
            action = attempt < maxAttempts
                    ? "Automatic recovery will retry within bounded attempts."
                    : "Review evidence and decide next action.";
            healable = attempt < maxAttempts;
        }

        return FailureDiagnosis.of(
                text.isBlank() ? "Pipeline stage failed: " + stage : text,
                why,
                component,
                FailureTaxonomy.canonicalize(category),
                "stage=" + stage + "; attempt=" + attempt + "/" + maxAttempts,
                healable && attempt < maxAttempts,
                attempt,
                action
        );
    }

    public boolean shouldAutoRetry(FailureDiagnosis diagnosis, int attempt, int maxAttempts) {
        if (diagnosis == null || attempt >= maxAttempts) {
            return false;
        }
        String category = diagnosis.category() == null ? "" : diagnosis.category();
        return switch (category) {
            case "INTENT_NORMALIZATION_FAILURE", "AI_RESPONSE_INVALID", "OLLAMA_REQUEST_FAILURE",
                 "FILTER", "FILTER_TARGET_RESOLUTION", "FILTER_CONTAINER_RESOLUTION",
                 "SEARCH", "SEARCH_RESOLUTION", "SEARCH_STATE_MISMATCH", "LOCATION_STATE_MISMATCH",
                 "ACTIONABILITY", "ACTIONABILITY_FAILURE", "DOM_DISCOVERY", "LOCATOR", "TARGET_NOT_FOUND",
                 "TARGET_AMBIGUOUS", "STALE_ELEMENT", "OVERLAY_BLOCKED",
                 "VISUAL_TARGET_RESOLUTION", "VISUAL_DOM_MISMATCH", "GENERATED_TEST", "VALIDATOR",
                 "VALIDATOR_FAILURE", "WAIT_STATE", "AI_INTENT",
                 "WRONG_HOST", "WRONG_PAGE", "WRONG_PAGE_STATE", "WRONG_STATE", "LOGIN_STATE_FAILURE",
                 "FRAME_RESOLUTION", "SHADOW_DOM_RESOLUTION" -> true;
            case "BUSINESS_STATE_MISMATCH", "INTENT_VALIDATION", "USER_INSTRUCTION" -> false;
            case "ASSERTION", "ASSERTION_FAILURE" -> diagnosis.autoRecoveryAttempted()
                    && diagnosis.aiDiagnosis() != null
                    && shouldRetryAssertion(diagnosis.aiDiagnosis());
            default -> false;
        };
    }

    private static boolean shouldRetryAssertion(AiDiagnosticResult ai) {
        if (ai == null) {
            return false;
        }
        String sub = ai.assertionSubCategory() == null ? "" : ai.assertionSubCategory();
        String root = ai.rootCause() == null ? "" : ai.rootCause();
        String classification = ai.normalizedClassification();
        return "WRONG_HOST".equals(classification)
                || sub.contains("WRONG")
                || root.contains("HOST")
                || root.contains("FORM_COMPLETION")
                || root.contains("APPLICATION_STATE")
                || root.contains("ASSERTION_NOT_REACHED");
    }

    private static String mapStageCategory(String stage) {
        if (stage == null) return "APPLICATION";
        return switch (stage.toUpperCase(Locale.ROOT)) {
            case "UNDERSTAND", "PLAN" -> "INTENT_NORMALIZATION_FAILURE";
            case "GENERATE", "QUALITY_GATE" -> "GENERATED_TEST";
            case "VALIDATE" -> "VALIDATOR";
            case "EXECUTE" -> "BROWSER";
            case "DIAGNOSE", "RECOVER" -> "UNKNOWN";
            default -> "APPLICATION";
        };
    }

    private static String mapStageComponent(String stage) {
        if (stage == null) return "Pipeline";
        return switch (stage.toUpperCase(Locale.ROOT)) {
            case "UNDERSTAND", "PLAN" -> "Test Understanding Agent";
            case "GENERATE" -> "Test Generator";
            case "QUALITY_GATE" -> "Quality Gate";
            case "VALIDATE" -> "Independent Validator";
            case "EXECUTE" -> "Action Execution Engine";
            default -> "Pipeline Orchestrator";
        };
    }

    private static boolean isFilterTargetResolution(String lower, String text) {
        if (containsAny(lower, "filter_target_resolution", "filter target resolution", "owned filter",
                "filter option", "checkbox under")) {
            return true;
        }
        if (!containsAny(lower, "multiple matching", "multiple matching")) {
            return false;
        }
        return containsAny(lower, "login", "cart", "seller", "explore", "flights", "header", "checkbox", "filter");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
