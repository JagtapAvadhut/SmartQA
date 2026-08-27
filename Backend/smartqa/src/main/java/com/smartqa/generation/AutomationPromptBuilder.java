package com.smartqa.generation;

import com.smartqa.browser.LocatorMemoryDocument;
import com.smartqa.browser.LocatorMemoryEntry;
import com.smartqa.intent.IntentContract;
import com.smartqa.intent.IntentFilter;
import com.smartqa.intent.IntentScenario;
import com.smartqa.intent.IntentStep;
import com.smartqa.testcase.TestCase;

public final class AutomationPromptBuilder {

    private AutomationPromptBuilder() {
    }

    public static String build(
            TestCase testCase,
            IntentContract intent,
            LocatorMemoryDocument locatorMemory,
            String className) {
        StringBuilder builder = new StringBuilder();
        builder.append("Class name: ").append(className).append('\n');
        builder.append("Method name: shouldRunRecordedFlow\n");
        builder.append("Test name: ").append(testCase.getName()).append('\n');
        builder.append("Natural language:\n").append(nullToEmpty(testCase.getNaturalLanguage())).append("\n\n");
        builder.append("Execution intent:\n");
        if (intent != null && intent.scenarios() != null) {
            for (IntentScenario scenario : intent.scenarios()) {
                builder.append("Scenario: ").append(scenario.name()).append('\n');
                if (scenario.steps() == null) {
                    continue;
                }
                for (IntentStep step : scenario.steps()) {
                    builder.append("  - ").append(step.action())
                            .append(" target=").append(nullToEmpty(step.target()))
                            .append(" value=").append(nullToEmpty(step.value()))
                            .append(" assertion=").append(nullToEmpty(step.assertion()));
                    appendFilter(builder, step.filter());
                    builder.append('\n');
                }
            }
        }
        builder.append('\n');
        builder.append("Verified locator memory (use ONLY these locators):\n");
        LocatorMemoryDocument compacted = LocatorMemoryPromptCompactor.compact(locatorMemory);
        if (compacted != null && compacted.entries() != null) {
            for (LocatorMemoryEntry entry : compacted.entries()) {
                builder.append("- stepId=").append(entry.stepId())
                        .append(" action=").append(entry.action())
                        .append(" target=").append(entry.semanticTarget())
                        .append(" locatorType=").append(entry.locatorType())
                        .append(" locator=").append(entry.resolvedLocator())
                        .append(" confidence=").append(entry.confidence())
                        .append(" controlType=").append(nullToEmpty(entry.controlType()))
                        .append(" url=").append(entry.pageUrl());
                if (entry.locatorCloud() != null && !entry.locatorCloud().isBlank()) {
                    builder.append(" alternatives=").append(entry.locatorCloud());
                }
                builder.append('\n');
            }
        }
        builder.append("""

                Hard rules:
                - Do not invent selectors.
                - Use only verified locator memory.
                - Do not invent elements.
                - Do not invent assertions.
                - Do not add actions that were not recorded.
                - After navigate and click, wait for page load state. Do not use Thread.sleep or waitForTimeout over 1000ms.
                - CRITICAL: If controlType=CUSTOM_DROPDOWN or controlType=COMBOBOX or controlType=LISTBOX, do NOT use selectOption(). Instead: click the control to open it, waitForTimeout(300), then find the option by getByRole(AriaRole.OPTION) or getByText() and click it.
                - Only use selectOption() when controlType=NATIVE_SELECT or controlType is empty/unknown.
                Framework: Playwright Java + JUnit 5.
                """);
        return builder.toString();
    }

    private static void appendFilter(StringBuilder builder, IntentFilter filter) {
        if (filter == null) {
            return;
        }
        builder.append(" filter=").append(nullToEmpty(filter.field()))
                .append(' ').append(nullToEmpty(filter.operator()))
                .append(' ').append(nullToEmpty(filter.displayValue()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
