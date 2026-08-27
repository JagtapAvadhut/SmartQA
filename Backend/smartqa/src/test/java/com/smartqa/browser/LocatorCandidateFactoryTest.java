package com.smartqa.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorCandidateFactoryTest {

    @Test
    void clickCandidatesPreferRoleThenText() {
        List<LocatorCandidateFactory.Candidate> candidates = LocatorCandidateFactory.candidates("click", "More information button");
        assertTrue(candidates.stream().anyMatch(item -> "More information".equals(item.resolvedLocator()) || item.resolvedLocator().endsWith("|More information")));
        assertTrue(candidates.stream().anyMatch(item -> "text".equals(item.locatorType())));
    }

    @Test
    void inputCandidatesIncludeLabelAndPlaceholder() {
        List<LocatorCandidateFactory.Candidate> candidates = LocatorCandidateFactory.candidates("input", "search");
        assertTrue(candidates.stream().anyMatch(item -> "label".equals(item.locatorType())));
        assertTrue(candidates.stream().anyMatch(item -> "placeholder".equals(item.locatorType())));
        assertTrue(candidates.stream().anyMatch(item -> item.resolvedLocator().contains("name='search'")));
    }

    @Test
    void searchCandidatesIncludeSearchboxComboboxAndTextbox() {
        List<LocatorCandidateFactory.Candidate> candidates = LocatorCandidateFactory.candidates("search", "search");
        assertTrue(candidates.stream().anyMatch(item -> item.resolvedLocator().startsWith("searchbox|")));
        assertTrue(candidates.stream().anyMatch(item -> item.resolvedLocator().startsWith("combobox|")));
        assertTrue(candidates.stream().anyMatch(item -> item.resolvedLocator().startsWith("textbox|")));
    }
}
