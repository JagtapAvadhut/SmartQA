package com.smartqa.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionSemanticNormalizerTest {

    @Test
    void selectBrandAkCheckboxIsCheckboxNotDropdown() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("select", "Brand AK checkbox", null);
        assertEquals(SupportedActions.CHECKBOX, rewrite.action());
        assertEquals("AK", rewrite.target());
        assertEquals("AK", rewrite.value());
        assertNotNull(rewrite.filter());
        assertEquals("Brand", rewrite.filter().field());
        assertEquals("AK", rewrite.filter().value());
        assertFalse(rewrite.target().toLowerCase().contains("dropdown"));
    }

    @Test
    void selectHpCheckboxIsCheckbox() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("select", "HP checkbox", null);
        assertEquals(SupportedActions.CHECKBOX, rewrite.action());
        assertEquals("HP", rewrite.target());
    }

    @Test
    void selectAcuarteAbcdCheckboxDropsNoiseKeepsAbcd() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("select", "acuarte ABCD checkbox", null);
        assertEquals(SupportedActions.CHECKBOX, rewrite.action());
        assertEquals("ABCD", rewrite.target());
        assertEquals("ABCD", rewrite.value());
        assertFalse(rewrite.target().toLowerCase().contains("dropdown"));
        assertFalse(rewrite.target().toLowerCase().contains("acuarte"));
    }

    @Test
    void dropdownPhraseStaysSelect() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("choose", "from Brand dropdown", "HP");
        assertEquals(SupportedActions.SELECT, rewrite.action());
        assertEquals("Brand", rewrite.target());
        assertEquals("HP", rewrite.value());
    }

    @Test
    void samsungRadioIsRadio() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("select", "Samsung radio button", null);
        assertEquals(SupportedActions.RADIO, rewrite.action());
        assertTrue(rewrite.target().toLowerCase().contains("samsung"));
    }

    @Test
    void laptopTextboxIsInput() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("enter", "laptop in textbox", null);
        assertEquals(SupportedActions.INPUT, rewrite.action());
        assertTrue(rewrite.value().toLowerCase().contains("laptop")
                || rewrite.target().toLowerCase().contains("laptop"));
    }

    @Test
    void loginButtonIsClick() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("click", "Login button", null);
        assertEquals(SupportedActions.CLICK, rewrite.action());
        assertTrue(rewrite.target().toLowerCase().contains("login"));
    }

    @Test
    void profileIconStaysClick() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("click", "profile icon", null);
        assertEquals("click", rewrite.action());
        assertEquals("profile icon", rewrite.target());
    }

    @Test
    void imageContainingSamsungIsVisualClick() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("click", "image containing Samsung", null);
        assertEquals(SupportedActions.CLICK, rewrite.action());
        assertEquals(ControlPhrase.VISUAL, rewrite.controlType());
        assertEquals("VISUAL_TARGET", rewrite.targetType());
        assertTrue(rewrite.target().toLowerCase().contains("samsung"));
    }

    @Test
    void plainSelectOptionStillSelect() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("select", "ESS", null);
        assertEquals("select", rewrite.action());
        assertEquals("ESS", rewrite.target());
    }

    @Test
    void addToCartPhraseIsAddToCart() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("click", "Add to cart", null);
        assertEquals(SupportedActions.ADD_TO_CART, rewrite.action());
        assertEquals("CART", rewrite.targetType());
    }

    @Test
    void expandSectionIsExpand() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("click", "expand Brand section", null);
        assertEquals(SupportedActions.EXPAND, rewrite.action());
    }

    @Test
    void increaseQuantityIsQuantity() {
        ActionSemanticNormalizer.Rewrite rewrite =
                ActionSemanticNormalizer.rewrite("click", "increase quantity", null);
        assertEquals(SupportedActions.QUANTITY, rewrite.action());
    }
}
