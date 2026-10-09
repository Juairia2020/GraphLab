package com.graphlab.engine.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeightFormatTest {

    @Test
    public void wholeNumbersHaveNoDecimals() {
        assertEquals("7", WeightFormat.format(7.0));
        assertEquals("-4", WeightFormat.format(-4.0));
        assertEquals("0", WeightFormat.format(-0.0));
        assertEquals("1000000", WeightFormat.format(1_000_000));
    }

    @Test
    public void fractionsKeepAtMostTwoDecimals() {
        assertEquals("2.5", WeightFormat.format(2.5));
        assertEquals("0.33", WeightFormat.format(1.0 / 3));
        assertEquals("-0.5", WeightFormat.format(-0.5));
        assertEquals("3.14", WeightFormat.format(3.14159));
    }

    @Test
    public void tinyFractionsDoNotShowAsNegativeZero() {
        assertEquals("0", WeightFormat.format(-0.001));
    }

    @Test
    public void parsesPlainNumbers() {
        assertEquals(7, WeightFormat.parse("7").value(), 1e-9);
        assertEquals(-3.5, WeightFormat.parse("-3.5").value(), 1e-9);
        assertEquals(0.5, WeightFormat.parse(".5").value(), 1e-9);
        assertEquals(12, WeightFormat.parse("  12 ").value(), 1e-9);
        assertEquals(2, WeightFormat.parse("+2").value(), 1e-9);
    }

    @Test
    public void acceptsADecimalCommaAndTheTypographicMinus() {
        assertEquals(2.5, WeightFormat.parse("2,5").value(), 1e-9);
        assertEquals(-4, WeightFormat.parse("\u22124").value(), 1e-9);
    }

    @Test
    public void rejectsEmptyAndJunk() {
        assertEquals(WeightFormat.Error.EMPTY, WeightFormat.parse("").error());
        assertEquals(WeightFormat.Error.EMPTY, WeightFormat.parse("   ").error());
        assertEquals(WeightFormat.Error.EMPTY, WeightFormat.parse(null).error());
        for (String bad : new String[] {"abc", "-", ".", "1.2.3", "1e3", "NaN", "Infinity", "1 2", "--1"}) {
            assertEquals(bad, WeightFormat.Error.NOT_A_NUMBER, WeightFormat.parse(bad).error());
        }
    }

    @Test
    public void rejectsHugeValuesButAcceptsTheLimit() {
        assertTrue(WeightFormat.parse("1000000").isValid());
        assertTrue(WeightFormat.parse("-1000000").isValid());
        assertEquals(WeightFormat.Error.TOO_LARGE, WeightFormat.parse("1000001").error());
        assertEquals(WeightFormat.Error.TOO_LARGE, WeightFormat.parse("99999999999999999999").error());
    }

    @Test
    public void minusZeroParsesToPlainZero() {
        assertEquals("0", WeightFormat.format(WeightFormat.parse("-0").value()));
        assertFalse(1 / WeightFormat.parse("-0").value() < 0);
    }
}
