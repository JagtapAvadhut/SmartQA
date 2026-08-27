package com.smartqa.browser.intelligence;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorRankerTest {

    @Test
    void uniqueWinnerPrefersExactAccessibleName() {
        ElementCandidate search = ElementCandidate.fromMap(Map.of(
                "tag", "input",
                "role", "searchbox",
                "accessibleName", "Search",
                "placeholder", "Search",
                "visible", true,
                "enabled", true
        ), 0);
        ElementCandidate other = ElementCandidate.fromMap(Map.of(
                "tag", "button",
                "role", "button",
                "accessibleName", "Apply",
                "text", "Apply",
                "visible", true,
                "enabled", true
        ), 1);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(search, other), "input", "Search");
        assertTrue(LocatorRanker.uniqueWinner(ranked));
        assertEquals("Search", ranked.getFirst().element().placeholder());
    }

    @Test
    void clickSearchPrefersButtonOverMenuSearchbox() {
        ElementCandidate searchBox = ElementCandidate.fromMap(Map.of(
                "tag", "input",
                "role", "searchbox",
                "accessibleName", "Search",
                "placeholder", "Search",
                "visible", true,
                "enabled", true,
                "clickable", true
        ), 0);
        ElementCandidate searchButton = ElementCandidate.fromMap(Map.of(
                "tag", "button",
                "role", "button",
                "accessibleName", "Search",
                "text", "Search",
                "visible", true,
                "enabled", true,
                "clickable", true
        ), 1);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(
                List.of(searchBox, searchButton), "click", "Search");
        assertEquals("button", ranked.getFirst().element().role());
    }

    @Test
    void checkboxActionPrefersOwnedFilterOverHeaderText() {
        ElementCandidate header = ElementCandidate.fromMap(Map.of(
                "tag", "a",
                "role", "link",
                "accessibleName", "AK",
                "text", "AK",
                "region", "HEADER",
                "inHeaderRegion", true,
                "visible", true,
                "enabled", true,
                "clickable", true
        ), 0);
        Map<String, Object> optionRaw = new HashMap<>();
        optionRaw.put("tag", "label");
        optionRaw.put("role", "checkbox");
        optionRaw.put("inputType", "checkbox");
        optionRaw.put("accessibleName", "AK");
        optionRaw.put("text", "AK");
        optionRaw.put("headingContext", "Brand");
        optionRaw.put("ancestorContext", "Brand filters");
        optionRaw.put("region", "FILTER_PANEL");
        optionRaw.put("visible", true);
        optionRaw.put("enabled", true);
        optionRaw.put("clickable", true);
        ElementCandidate option = ElementCandidate.fromMap(optionRaw, 1);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rankOwned(
                List.of(header, option), "checkbox", "AK", "Brand", "MIDDLE_LEFT");
        assertEquals(option, ranked.getFirst().element());
    }

    @Test
    void brandFilterOutranksDuplicateProductText() {
        ElementCandidate filterHp = ElementCandidate.fromMap(Map.of(
                "tag", "input",
                "role", "checkbox",
                "accessibleName", "HP",
                "text", "HP",
                "parentContext", "Brand filter HP Dell",
                "visible", true,
                "enabled", true
        ), 0);
        ElementCandidate product = ElementCandidate.fromMap(Map.of(
                "tag", "a",
                "role", "link",
                "accessibleName", "HP Pavilion Laptop",
                "text", "HP Pavilion Laptop",
                "parentContext", "product card",
                "visible", true,
                "enabled", true
        ), 1);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(filterHp, product), "click", "HP");
        assertEquals("HP", ranked.getFirst().element().text());
        assertTrue(LocatorRanker.uniqueWinner(ranked) || ranked.getFirst().score() > ranked.get(1).score());
    }

    @Test
    void ambiguousButtonsAreNotUniqueWinners() {
        ElementCandidate search = button("Search");
        ElementCandidate apply = button("Apply");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(search, apply), "click", "the button");
        assertFalse(LocatorRanker.uniqueWinner(ranked));
    }

    @Test
    void profileIconHintPrefersRightHeaderIconButton() {
        ElementCandidate cart = iconButton("Button Double Tap to perform action", "", "header stack", "1226,20,40,40");
        ElementCandidate profile = iconButton("", "", "header stack", "1282,20,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(cart, profile), "click", "profile icon");
        assertEquals(profile, ranked.getFirst().element());
    }

    @Test
    void exactLoginTextPrefersCompactClickableLeaf() {
        ElementCandidate large = ElementCandidate.fromMap(Map.of(
                "tag", "div",
                "accessibleName", "Login",
                "text", "Login",
                "visible", true,
                "enabled", true,
                "clickable", true,
                "boundingBox", "0,0,1400,900"
        ), 0);
        Map<String, Object> leafRaw = new HashMap<>();
        leafRaw.put("tag", "div");
        leafRaw.put("accessibleName", "Login");
        leafRaw.put("text", "Login");
        leafRaw.put("visible", true);
        leafRaw.put("enabled", true);
        leafRaw.put("clickable", true);
        leafRaw.put("boundingBox", "1179,62,144,36");
        ElementCandidate leaf = ElementCandidate.fromMap(leafRaw, 1);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(large, leaf), "click", "Login");
        assertEquals(leaf, ranked.getFirst().element());
    }

    @Test
    void weakAriaLabelDoesNotOutrankSemanticProfileButton() {
        ElementCandidate weak = iconButton("Button Double Tap to perform action", "", "header", "1180,20,40,40");
        ElementCandidate named = iconButton("My Account", "account", "header", "1280,20,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(weak, named), "click", "profile icon");
        assertEquals(named, ranked.getFirst().element());
    }

    @Test
    void clickPrefersActionableButtonOverSvgLeaf() {
        Map<String, Object> svgRaw = new HashMap<>();
        svgRaw.put("tag", "svg");
        svgRaw.put("hasIcon", true);
        svgRaw.put("clickable", false);
        svgRaw.put("inHeaderRegion", true);
        svgRaw.put("visible", true);
        svgRaw.put("enabled", true);
        svgRaw.put("boundingBox", "1282,20,24,24");
        svgRaw.put("actionableSelector", "header > button");
        svgRaw.put("actionableTag", "button");
        svgRaw.put("actionableRole", "button");
        ElementCandidate svg = ElementCandidate.fromMap(svgRaw, 0);
        ElementCandidate button = iconButton("", "", "header stack", "1282,20,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(svg, button), "click", "profile icon");
        assertEquals(button, ranked.getFirst().element());
    }

    @Test
    void profileHintPenalizesNearbyCartEvenWhenCartHasStrongName() {
        ElementCandidate cart = iconButton("Shopping cart", "cart", "header stack", "1226,20,40,40");
        ElementCandidate profile = iconButton("", "", "header stack", "1282,20,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(cart, profile), "click", "profile icon");
        assertEquals(profile, ranked.getFirst().element());
        assertTrue(ranked.getFirst().score() > ranked.get(1).score());
    }

    @Test
    void cartIconHintPrefersSemanticCartButton() {
        ElementCandidate cart = iconButton("Shopping cart", "cart-control", "header", "300,20,40,40");
        ElementCandidate profile = iconButton("", "profile-control", "header", "360,20,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(profile, cart), "click", "cart icon");
        assertEquals(cart, ranked.getFirst().element());
    }

    @Test
    void searchIconHintPrefersSearchSemanticButton() {
        ElementCandidate menu = iconButton("Main menu", "menu", "header", "200,20,40,40");
        ElementCandidate search = iconButton("Search", "search", "header", "260,20,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(menu, search), "click", "search icon");
        assertEquals(search, ranked.getFirst().element());
    }

    @Test
    void menuIconHintPrefersMenuSemanticButton() {
        ElementCandidate menu = iconButton("Menu", "hamburger", "header", "220,20,40,40");
        ElementCandidate search = iconButton("Search", "search", "header", "260,20,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(search, menu), "click", "menu icon");
        assertEquals(menu, ranked.getFirst().element());
    }

    @Test
    void topRightLocationHintBoostsRightHeaderIcon() {
        ElementCandidate left = iconButton("", "icon", "header", "40,20,40,40");
        ElementCandidate right = iconButton("", "icon", "header", "1280,20,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(
                List.of(left, right), "click", "profile icon", "TOP_RIGHT");
        assertEquals(right, ranked.getFirst().element());
        assertTrue(ranked.getFirst().score() > ranked.get(1).score());
    }

    @Test
    void profileHintPenalizesCartSemantics() {
        ElementCandidate cart = iconButton("Shopping cart", "cart-control", "header", "1280,20,40,40");
        ElementCandidate profile = iconButton("", "profile-control", "header", "1220,20,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(
                List.of(cart, profile), "click", "profile icon", "TOP_RIGHT");
        assertEquals(profile, ranked.getFirst().element());
    }

    @Test
    void profileHintPrefersRightmostWhenBothUnlabeled() {
        ElementCandidate leftCartish = iconButton("Button Double Tap to perform action", "", "header stack", "1226,20,40,40");
        ElementCandidate rightProfile = iconButton("", "", "header stack", "1282,20,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(
                List.of(leftCartish, rightProfile), "click", "profile icon");
        assertEquals(rightProfile, ranked.getFirst().element());
    }

    @Test
    void profileHintPrefersCompactButtonOverWideWrapper() {
        ElementCandidate wideWrapper = iconButton("", "", "header stack", "1603,16,205,52");
        ElementCandidate compactProfile = iconButton("", "", "header stack", "1762,22,40,40");
        // Force wrapper tag to div (common React-Native-Web host).
        Map<String, Object> wrapperRaw = new HashMap<>();
        wrapperRaw.put("tag", "div");
        wrapperRaw.put("role", "none");
        wrapperRaw.put("accessibleName", "");
        wrapperRaw.put("className", "");
        wrapperRaw.put("parentContext", "header stack");
        wrapperRaw.put("visible", true);
        wrapperRaw.put("enabled", true);
        wrapperRaw.put("clickable", true);
        wrapperRaw.put("inHeaderRegion", true);
        wrapperRaw.put("hasIcon", true);
        wrapperRaw.put("boundingBox", "1603,16,205,52");
        wrapperRaw.put("actionableSelector", "div#rightButtonStack > div");
        wideWrapper = ElementCandidate.fromMap(wrapperRaw, 0);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(
                List.of(wideWrapper, compactProfile), "click", "profile icon");
        assertEquals(compactProfile, ranked.getFirst().element());
    }

    @Test
    void profileHintPrefersUnlabeledHeaderButtonOverDoubleTapNeighbor() {
        ElementCandidate cart = iconButton("Button Double Tap to perform action", "", "header stack", "1706,22,40,40");
        ElementCandidate profile = iconButton("", "", "header stack", "1762,22,40,40");
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(cart, profile), "click", "profile icon");
        assertEquals(profile, ranked.getFirst().element());
    }

    @Test
    void searchPrefersTextboxOverSubmitButtonWithSameName() {
        ElementCandidate button = ElementCandidate.fromMap(Map.of(
                "tag", "button",
                "role", "button",
                "inputType", "submit",
                "accessibleName", "Search for Products, Brands and More",
                "ariaLabel", "Search for Products, Brands and More",
                "visible", true,
                "enabled", true
        ), 0);
        ElementCandidate field = ElementCandidate.fromMap(Map.of(
                "tag", "input",
                "role", "combobox",
                "inputType", "text",
                "accessibleName", "Search for Products, Brands and More",
                "placeholder", "Search for Products, Brands and More",
                "visible", true,
                "enabled", true
        ), 1);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(
                List.of(button, field), "search", "Search for Products, Brands and More");
        assertEquals(field, ranked.getFirst().element());
        assertTrue(ranked.getFirst().score() > ranked.get(1).score());
    }

    @Test
    void searchDemotesCityFieldWhenQueryIsNotLocation() {
        ElementCandidate city = ElementCandidate.fromMap(Map.of(
                "tag", "input",
                "role", "textbox",
                "accessibleName", "City",
                "placeholder", "Enter city",
                "visible", true,
                "enabled", true
        ), 0);
        ElementCandidate search = ElementCandidate.fromMap(Map.of(
                "tag", "input",
                "role", "combobox",
                "accessibleName", "Search",
                "placeholder", "Search products",
                "region", "SEARCH_AREA",
                "visible", true,
                "enabled", true
        ), 1);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(List.of(city, search), "search", "search");
        assertEquals(search, ranked.getFirst().element());
    }

    @Test
    void clickFilterPrefersCompactChromeOverCompanyTitleContainingFilter() {
        ElementCandidate company = ElementCandidate.fromMap(Map.of(
                "tag", "a",
                "role", "link",
                "accessibleName", "Anush Filters Fabrics",
                "text", "Anush Filters Fabrics TrustSEAL",
                "region", "CONTENT",
                "visible", true,
                "enabled", true,
                "clickable", true,
                "boundingBox", "80,400,640,180"
        ), 0);
        ElementCandidate filterBtn = ElementCandidate.fromMap(Map.of(
                "tag", "button",
                "role", "button",
                "accessibleName", "Filter",
                "text", "Filter",
                "region", "SIDEBAR",
                "visible", true,
                "enabled", true,
                "clickable", true,
                "hasIcon", true,
                "boundingBox", "16,180,72,36"
        ), 1);
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rank(
                List.of(company, filterBtn), "click", "Filter");
        assertEquals(filterBtn, ranked.getFirst().element());
    }

    @Test
    void unlabeledCompactFilterIconOutranksLongContentTitleContainingFilter() {
        ElementCandidate company = ElementCandidate.fromMap(Map.of(
                "tag", "a",
                "role", "link",
                "accessibleName", "Anush Filters Fabrics TrustSEAL Verified Exporter",
                "text", "Anush Filters Fabrics TrustSEAL Verified Exporter",
                "region", "CONTENT",
                "visible", true,
                "enabled", true,
                "clickable", true,
                "boundingBox", "80,400,720,220"
        ), 0);
        Map<String, Object> icon = new java.util.HashMap<>();
        icon.put("tag", "button");
        icon.put("role", "button");
        icon.put("accessibleName", "");
        icon.put("text", "");
        icon.put("ariaLabel", "");
        icon.put("hasIcon", true);
        icon.put("inHeaderRegion", true);
        icon.put("region", "HEADER");
        icon.put("parentContext", "filters sort apply");
        icon.put("visible", true);
        icon.put("enabled", true);
        icon.put("clickable", true);
        icon.put("boundingBox", "24,18,36,36");
        ElementCandidate filterIcon = ElementCandidate.fromMap(icon, 1);
        var ranked = LocatorRanker.rank(java.util.List.of(company, filterIcon), "click", "Filter icon");
        assertEquals(filterIcon, ranked.getFirst().element());
    }

    private static ElementCandidate button(String name) {
        return ElementCandidate.fromMap(Map.of(
                "tag", "button",
                "role", "button",
                "accessibleName", name,
                "text", name,
                "visible", true,
                "enabled", true
        ), 0);
    }

    private static ElementCandidate iconButton(String accessibleName, String className, String context, String bbox) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("tag", "button");
        raw.put("role", "button");
        raw.put("accessibleName", accessibleName);
        raw.put("className", className);
        raw.put("parentContext", context);
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", true);
        raw.put("inHeaderRegion", true);
        raw.put("hasIcon", true);
        raw.put("boundingBox", bbox);
        raw.put("actionableSelector", "header > button");
        return ElementCandidate.fromMap(raw, 0);
    }

    @Test
    void shortOptionTokenDoesNotMatchLongerBrandOrRatingChip() {
        ElementCandidate ak = filterOption("brand-ak", "AK", "Brand");
        ElementCandidate akraft = filterOption("brand-akraft", "AKRAFT", "Brand");
        ElementCandidate rating = filterOption("rating-4", "4 & above", "Customer Ratings");
        assertTrue(LocatorRanker.optionMatches(ak, "ak"));
        assertFalse(LocatorRanker.optionMatches(akraft, "ak"));
        assertFalse(LocatorRanker.optionMatches(rating, "ak"));
        List<LocatorRanker.RankedElement> ranked = LocatorRanker.rankOwned(
                List.of(ak, akraft, rating), "checkbox", "AK", "Brand", null);
        assertFalse(ranked.isEmpty());
        assertEquals("brand-ak", ranked.getFirst().element().candidateId());
    }

    private static ElementCandidate filterOption(String id, String text, String heading) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("candidateId", id);
        raw.put("tag", "input");
        raw.put("role", "checkbox");
        raw.put("inputType", "checkbox");
        raw.put("accessibleName", text);
        raw.put("text", text);
        raw.put("headingContext", heading);
        raw.put("region", "FILTER_PANEL");
        raw.put("visible", true);
        raw.put("enabled", true);
        raw.put("clickable", true);
        return ElementCandidate.fromMap(raw, 0);
    }
}
