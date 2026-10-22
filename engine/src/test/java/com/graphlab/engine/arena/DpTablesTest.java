package com.graphlab.engine.arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Fill the table for the MS12 DP algorithms: every blank must follow the recurrence from the cells around it. */
public class DpTablesTest {

    private static int value(String text) {
        return text.equals("∞") ? Integer.MAX_VALUE / 2 : Integer.parseInt(text);
    }

    @Test
    public void everyBlankFollowsTheRecurrenceOfItsAlgorithm() {
        Set<String> seen = new HashSet<>();
        for (Level level : Level.values()) {
            for (long seed = 0; seed < 400; seed++) {
                Question q = QuestionFactory.create(QuestionKind.FILL_TABLE, level, new java.util.Random(seed).nextLong(), new HashSet<>());
                Task task = q.task();
                assertEquals(Interaction.TABLE, task.interaction());
                if (q.topic().equals("knapsack-01")) {
                    seen.add(q.topic());
                    continue;
                }
                seen.add(q.topic());
                int cols = task.columns();
                int rows = task.items().size() / cols;
                assertEquals(rows, task.rowHeaders().size());
                assertEquals(cols, task.colHeaders().size());
                int[][] t = new int[rows][cols];
                int blank = 0;
                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) {
                        String cell = task.items().get(i * cols + j);
                        t[i][j] = cell.isEmpty() ? task.solution().get(blank++) : value(cell);
                    }
                }
                assertEquals(task.solution().size(), blank);
                for (int pos : task.blanks()) {
                    int i = pos / cols;
                    int j = pos % cols;
                    assertTrue(i >= 1 && j >= 1);
                    int expected;
                    switch (q.topic()) {
                        case "coin-change" -> {
                            int coin = Integer.parseInt(task.rowHeaders().get(i).substring(5));
                            int skip = t[i - 1][j];
                            int take = j >= coin && t[i][j - coin] < Integer.MAX_VALUE / 2 ? t[i][j - coin] + 1 : Integer.MAX_VALUE / 2;
                            expected = Math.min(skip, take);
                        }
                        case "lcs" -> expected = task.rowHeaders().get(i).equals(task.colHeaders().get(j))
                                ? t[i - 1][j - 1] + 1 : Math.max(t[i - 1][j], t[i][j - 1]);
                        case "edit-distance" -> expected = task.rowHeaders().get(i).equals(task.colHeaders().get(j))
                                ? t[i - 1][j - 1] : 1 + Math.min(t[i - 1][j - 1], Math.min(t[i - 1][j], t[i][j - 1]));
                        default -> throw new AssertionError(q.topic());
                    }
                    assertEquals(q.topic() + " cell " + i + "," + j + " seed " + seed, expected, t[i][j]);
                }
                // a perfect answer is right, a wrong one is not
                int[] right = task.solution().stream().mapToInt(Integer::intValue).toArray();
                assertTrue(q.isCorrect(task.mistakes(right)));
                right[0] += 1;
                assertTrue(!q.isCorrect(task.mistakes(right)));
            }
        }
        assertEquals(Set.of("knapsack-01", "coin-change", "lcs", "edit-distance"), seen);
    }

    @Test
    public void theSameSeedMakesTheSameTable() {
        Question a = QuestionFactory.create(QuestionKind.FILL_TABLE, Level.MEDIUM, 77, new HashSet<>());
        Question b = QuestionFactory.create(QuestionKind.FILL_TABLE, Level.MEDIUM, 77, new HashSet<>());
        assertEquals(a, b);
        assertEquals(List.of(), a.options());
    }
}
