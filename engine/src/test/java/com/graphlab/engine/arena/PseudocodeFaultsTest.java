package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.algo.Algorithms;
import com.graphlab.engine.trace.Algorithm;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class PseudocodeFaultsTest {
    @Test
    public void exactlyOneLineChangesAndItReallyDiffers() {
        int breakableAlgorithms = 0;
        for (Algorithm a : Algorithms.all()) {
            List<String> code = a.pseudocode();
            if (PseudocodeFaults.breakable(code).isEmpty()) {
                assertNull(a.id(), PseudocodeFaults.inject(code, new Random(1)));
                continue;
            }
            breakableAlgorithms++;
            for (long seed = 0; seed < 20; seed++) {
                PseudocodeFaults.Fault f = PseudocodeFaults.inject(code, new Random(seed));
                assertEquals(code.size(), f.lines().size());
                int changed = 0;
                for (int i = 0; i < code.size(); i++) {
                    if (!code.get(i).equals(f.lines().get(i))) {
                        changed++;
                        assertEquals(i, f.lineIndex());
                    }
                }
                assertEquals(a.id() + " seed " + seed, 1, changed);
                assertEquals(code.get(f.lineIndex()), f.original());
                assertNotEquals(f.original(), f.broken());
                assertTrue(f.why().length() > 10);
            }
        }
        assertTrue("many algorithms can be broken: " + breakableAlgorithms, breakableAlgorithms >= 10);
    }

    @Test
    public void sameSeedSameFault() {
        List<String> code = Algorithms.byId("dijkstra").pseudocode();
        assertEquals(PseudocodeFaults.inject(code, new Random(5)), PseudocodeFaults.inject(code, new Random(5)));
    }

    @Test
    public void knownExamples() {
        PseudocodeFaults.Fault f = PseudocodeFaults.inject(List.of("take the smallest item"), new Random(0));
        assertEquals("take the largest item", f.broken());
        f = PseudocodeFaults.inject(List.of("if list[j] < pivot: swap"), new Random(0));
        assertEquals("if list[j] > pivot: swap", f.broken());
        assertTrue(PseudocodeFaults.breakable(List.of("nothing to flip here")).isEmpty());
    }
}
