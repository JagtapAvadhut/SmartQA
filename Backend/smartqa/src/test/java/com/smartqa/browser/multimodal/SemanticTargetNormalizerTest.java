package com.smartqa.browser.multimodal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticTargetNormalizerTest {

    @Test
    void expandBrandAndModelStaysAtomicExpandable() {
        SemanticTargetNormalizer.NormalizedTarget intent =
                SemanticTargetNormalizer.normalize("expand", "Brand & Model");
        assertFalse(intent.isFilterOption());
        assertEquals("Brand & Model", intent.value());
        assertTrue(intent.semanticField() == null || intent.semanticField().isBlank());
        assertFalse("checkbox".equalsIgnoreCase(intent.controlType()));
        assertTrue("expand".equalsIgnoreCase(intent.action()) || "expandable".equalsIgnoreCase(intent.controlType()));
    }

    @Test
    void customerRatingsAndPriceRangeStayAtomic() {
        SemanticTargetNormalizer.NormalizedTarget ratings =
                SemanticTargetNormalizer.normalize("expand", "Customer Ratings");
        assertEquals("Customer Ratings", ratings.value());
        assertFalse(intentIsCheckbox(ratings));
        SemanticTargetNormalizer.NormalizedTarget price =
                SemanticTargetNormalizer.normalize("click", "Price Range dropdown");
        assertTrue(price.value().toLowerCase().contains("price"));
        assertFalse(intentIsCheckbox(price));
    }

    private static boolean intentIsCheckbox(SemanticTargetNormalizer.NormalizedTarget intent) {
        return "checkbox".equalsIgnoreCase(intent.controlType())
                && intent.isFilterOption()
                && intent.value() != null
                && intent.value().startsWith("&");
    }

    @Test
    void selectBrandAkCheckboxStaysCheckbox() {
        SemanticTargetNormalizer.NormalizedTarget intent =
                SemanticTargetNormalizer.normalize("select", "select Brand AK checkbox");
        assertTrue(intent.isFilterOption());
        assertEquals("Brand", intent.semanticField());
        assertEquals("AK", intent.value());
        assertEquals("checkbox", intent.action());
        assertEquals("checkbox", intent.controlType());
    }

    @Test
    void selectAcuarteAbcdCheckboxExtractsAbcd() {
        SemanticTargetNormalizer.NormalizedTarget intent =
                SemanticTargetNormalizer.normalize("select", "select acuarte ABCD checkbox");
        assertEquals("ABCD", intent.value());
        assertEquals("checkbox", intent.action());
        assertFalse(intent.value().toLowerCase().contains("dropdown"));
    }

    @Test
    void selectBrandAkBecomesFilterOption() {
        SemanticTargetNormalizer.NormalizedTarget intent = SemanticTargetNormalizer.normalize("click", "select Brand AK");
        assertTrue(intent.isFilterOption());
        assertEquals("Brand", intent.semanticField());
        assertEquals("AK", intent.value());
        assertEquals("FILTER_OPTION", intent.targetType());
    }

    @Test
    void checkboxLocationEqualsBecomesFilterOption() {
        SemanticTargetNormalizer.NormalizedTarget intent =
                SemanticTargetNormalizer.normalize("checkbox", "CHECKBOX [MIDDLE_LEFT] AK = AK");
        assertTrue(intent.isFilterOption());
        assertEquals("AK", intent.value());
        assertEquals("MIDDLE_LEFT", intent.location());
    }

    @Test
    void imageContainingTextIsVisualImageText() {
        SemanticTargetNormalizer.NormalizedTarget intent =
                SemanticTargetNormalizer.normalize("click", "Click the image containing Samsung");
        assertTrue(intent.isVisual());
        assertEquals(TargetType.IMAGE_TEXT_TARGET, intent.targetType());
        assertTrue(intent.value().toLowerCase().contains("samsung"));
    }

    @Test
    void canvasInstructionIsVisualImage() {
        SemanticTargetNormalizer.NormalizedTarget intent =
                SemanticTargetNormalizer.normalize("click", "Click the canvas with the promo");
        assertTrue(intent.isVisual());
        assertEquals(TargetType.IMAGE_TARGET, intent.targetType());
    }

    @Test
    void bannerInstructionIsVisual() {
        SemanticTargetNormalizer.NormalizedTarget intent =
                SemanticTargetNormalizer.normalize("click", "Click the sneaker project banner");
        assertTrue(intent.isVisual());
        assertEquals(TargetType.BANNER, intent.targetType());
    }

    @Test
    void brandAkUnderHeadingParses() {
        SemanticTargetNormalizer.NormalizedTarget intent = SemanticTargetNormalizer.normalize("checkbox", "AK under Brand");
        assertTrue(intent.isFilterOption());
        assertEquals("Brand", intent.semanticField());
        assertEquals("AK", intent.value());
    }

    @Test
    void twoWordNavigationClickIsNotAFilterOption() {
        SemanticTargetNormalizer.NormalizedTarget seoul =
                SemanticTargetNormalizer.normalize("click", "Seoul Streetwear");
        assertFalse(seoul.isFilterOption());
        assertEquals("GENERIC", seoul.targetType());
        assertTrue(seoul.value().toLowerCase().contains("seoul"));

        SemanticTargetNormalizer.NormalizedTarget store =
                SemanticTargetNormalizer.normalize("click", "Korean Store");
        assertFalse(store.isFilterOption());
        assertEquals("GENERIC", store.targetType());
    }

    @Test
    void clickKnownFilterFieldStillBecomesFilterOption() {
        SemanticTargetNormalizer.NormalizedTarget intent = SemanticTargetNormalizer.normalize("click", "Brand AK");
        assertTrue(intent.isFilterOption());
        assertEquals("Brand", intent.semanticField());
        assertEquals("AK", intent.value());
    }
}
