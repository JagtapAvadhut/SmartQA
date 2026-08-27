package com.smartqa.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FilterIntentParserTest {

    @Test
    void brandAndModelStaysAtomic() {
        IntentFilter filter = FilterIntentParser.parse("Brand & Model");
        assertNotNull(filter);
        assertEquals("Brand & Model", filter.field());
        assertNull(filter.value());
    }

    @Test
    void brandHpBecomesEquals() {
        IntentFilter filter = FilterIntentParser.parse("Brand HP");
        assertNotNull(filter);
        assertEquals("Brand", filter.field());
        assertEquals("equals", filter.operator());
        assertEquals("HP", filter.value());
        assertNull(filter.min());
        assertNull(filter.max());
    }

    @Test
    void priceToRangeBecomesBetween() {
        IntentFilter filter = FilterIntentParser.parse("Price 40000 to 60000");
        assertNotNull(filter);
        assertEquals("Price", filter.field());
        assertEquals("between", filter.operator());
        assertEquals(40000d, filter.min());
        assertEquals(60000d, filter.max());
    }

    @Test
    void priceBetweenAndBecomesBetween() {
        IntentFilter filter = FilterIntentParser.parse("Price between 40000 and 60000");
        assertNotNull(filter);
        assertEquals("Price", filter.field());
        assertEquals("between", filter.operator());
        assertEquals(40000d, filter.min());
        assertEquals(60000d, filter.max());
    }

    @Test
    void priceMinMaxAndPlusBoundParse() {
        IntentFilter minMax = FilterIntentParser.parse("Price min 30000 max 60000");
        assertNotNull(minMax);
        assertEquals("between", minMax.operator());
        assertEquals(30000d, minMax.min());
        assertEquals(60000d, minMax.max());
        IntentFilter plus = FilterIntentParser.parse("Price 60000 to 75000+");
        assertNotNull(plus);
        assertEquals("between", plus.operator());
        assertEquals(60000d, plus.min());
        assertEquals(75000d, plus.max());
    }
}
