package com.graphlab.engine.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.graphlab.engine.arena.Level;
import com.graphlab.engine.arena.QuestionFactory;
import com.graphlab.engine.arena.QuestionKind;
import com.graphlab.engine.io.MiniJson;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Test;

/** The MS11 rule: every content file ships with a schema version, loads, and every reference in it resolves. */
public class ContentFilesTest {
    private static Map<?, ?> read(String resource) throws Exception {
        try (java.io.InputStream in = ContentFilesTest.class.getResourceAsStream(resource)) {
            assertNotNull(resource, in);
            return (Map<?, ?>) MiniJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void everyContentFileHasTheSchemaVersionTheParserExpects() throws Exception {
        assertEquals((double) CardBank.SCHEMA_VERSION, ((Number) read(CardBank.RESOURCE).get("schemaVersion")).doubleValue(), 0);
    }

    @Test
    public void everyFileLoadsAndEveryReferenceResolves() {
        CardBank cards = CardBank.bundled();          // topics are algorithm ids, best-tool options are algorithm names
        assertTrue(cards.cards().size() >= 90);
    }

    @Test
    public void everyActivityThatUsesCardsCanMakeAQuestionAtEveryLevel() {
        for (QuestionKind kind : QuestionKind.values()) {
            if (!kind.usesCardBank()) {
                continue;
            }
            for (Level level : Level.values()) {
                for (long seed = 0; seed < 20; seed++) {
                    assertNotNull(kind + " " + level, QuestionFactory.create(kind, level, seed));
                }
            }
        }
    }

    @Test
    public void everyIdInTheCardFileIsUnique() {
        assertEquals(CardBank.bundled().cards().size(),
                CardBank.bundled().cards().stream().map(Card::id).distinct().count());
    }
}
