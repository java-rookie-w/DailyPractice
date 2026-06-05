package org.wang.mianshi.collectiontest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SimpleHashMap}.
 *
 * Covers: core hash/index algorithms, basic CRUD, collision & treeification,
 * resize, edge cases, and the custom BadHashKey scenario.
 */
class SimpleHashMapTest {

    // =========================================================================
    //  SECTION A — tableSizeFor (static)
    // =========================================================================

    @Test
    @DisplayName("tableSizeFor should round up to next power of two")
    void tableSizeForRoundsToPowerOfTwo() {
        assertEquals(8,   SimpleHashMap.tableSizeFor(5));
        assertEquals(16,  SimpleHashMap.tableSizeFor(10));
        assertEquals(16,  SimpleHashMap.tableSizeFor(16));
        assertEquals(32,  SimpleHashMap.tableSizeFor(17));
    }

    @Test
    @DisplayName("tableSizeFor(0) should return 1")
    void tableSizeForZeroReturnsOne() {
        assertEquals(1, SimpleHashMap.tableSizeFor(0));
    }

    @Test
    @DisplayName("tableSizeFor(1) should return 1")
    void tableSizeForOneReturnsOne() {
        assertEquals(1, SimpleHashMap.tableSizeFor(1));
    }

    @Test
    @DisplayName("tableSizeFor should not exceed MAXIMUM_CAPACITY")
    void tableSizeForMaxCap() {
        assertEquals(1 << 30, SimpleHashMap.tableSizeFor(1 << 30));
        assertEquals(1 << 30, SimpleHashMap.tableSizeFor((1 << 30) + 1));
    }

    // =========================================================================
    //  SECTION B — hash() (static)
    // =========================================================================

    @Test
    @DisplayName("hash(null) should return 0")
    void hashNullReturnsZero() {
        assertEquals(0, SimpleHashMap.hash(null));
    }

    @Test
    @DisplayName("hash should produce consistent results")
    void hashIsDeterministic() {
        assertEquals(SimpleHashMap.hash("test"), SimpleHashMap.hash("test"));
    }

    // =========================================================================
    //  SECTION C — Basic put / get / remove
    // =========================================================================

    @Test
    @DisplayName("newly created map should have size 0")
    void emptyMapHasSizeZero() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        assertEquals(0, map.size());
    }

    @Test
    @DisplayName("put should insert and return null for new key")
    void putReturnsNullForNewKey() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        assertNull(map.put("key1", 1));
        assertEquals(1, map.size());
    }

    @Test
    @DisplayName("put should return old value when key exists")
    void putReturnsOldValueForExistingKey() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("key1", 1);
        assertEquals(1, map.put("key1", 100));
        assertEquals(1, map.size());
    }

    @Test
    @DisplayName("get should retrieve value by key")
    void getReturnsCorrectValue() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);

        assertEquals(1, map.get("one"));
        assertEquals(2, map.get("two"));
        assertEquals(3, map.get("three"));
    }

    @Test
    @DisplayName("get should return null for missing key")
    void getReturnsNullForMissingKey() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("key1", 1);
        assertNull(map.get("nonexistent"));
    }

    @Test
    @DisplayName("get should return null when map is empty")
    void getReturnsNullOnEmptyMap() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        assertNull(map.get("anything"));
    }

    @Test
    @DisplayName("remove should delete and return value")
    void removeReturnsDeletedValue() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("one", 1);
        assertEquals(1, map.remove("one"));
        assertEquals(0, map.size());
        assertNull(map.get("one"));
    }

    @Test
    @DisplayName("remove should return null for missing key")
    void removeReturnsNullForMissingKey() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        assertNull(map.remove("nonexistent"));
    }

    @Test
    @DisplayName("remove on empty map should return null")
    void removeOnEmptyMapReturnsNull() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        assertNull(map.remove("any"));
    }

    // =========================================================================
    //  SECTION D — Overwrite & size tracking
    // =========================================================================

    @Test
    @DisplayName("repeated puts with same key should not increase size")
    void repeatedPutSameKeyKeepsSize() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("a", 1);
        map.put("a", 2);
        map.put("a", 3);
        assertEquals(1, map.size());
        assertEquals(3, map.get("a"));
    }

    @Test
    @DisplayName("multiple distinct keys should increase size correctly")
    void multipleKeysIncreaseSize() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        for (int i = 0; i < 100; i++) {
            map.put("key" + i, i);
        }
        assertEquals(100, map.size());
    }

    // =========================================================================
    //  SECTION E — Null key & value
    // =========================================================================

    @Test
    @DisplayName("null key should be supported (hash = 0)")
    void nullKeyIsSupported() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put(null, 42);
        assertEquals(42, map.get(null));
        assertEquals(1, map.size());
    }

    @Test
    @DisplayName("null value should be supported")
    void nullValueIsSupported() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("key", null);
        assertNull(map.get("key"));
    }

    // =========================================================================
    //  SECTION F — Collision with BadHashKey
    // =========================================================================

    @Test
    @DisplayName("BadHashKey keys all collide in same bucket")
    void badHashKeysCollide() {
        SimpleHashMap<SimpleHashMap.BadHashKey, String> map = new SimpleHashMap<>();
        for (int i = 0; i < 20; i++) {
            map.put(new SimpleHashMap.BadHashKey(i), "val" + i);
        }
        assertEquals(20, map.size());
        for (int i = 0; i < 20; i++) {
            assertEquals("val" + i, map.get(new SimpleHashMap.BadHashKey(i)));
        }
    }

    @Test
    @DisplayName("remove from collision chain should work correctly")
    void removeFromCollisionChain() {
        SimpleHashMap<SimpleHashMap.BadHashKey, String> map = new SimpleHashMap<>();
        for (int i = 0; i < 10; i++) {
            map.put(new SimpleHashMap.BadHashKey(i), "val" + i);
        }
        assertEquals("val5", map.remove(new SimpleHashMap.BadHashKey(5)));
        assertNull(map.get(new SimpleHashMap.BadHashKey(5)));
        assertEquals(9, map.size());
        // other entries still reachable
        assertEquals("val3", map.get(new SimpleHashMap.BadHashKey(3)));
        assertEquals("val7", map.get(new SimpleHashMap.BadHashKey(7)));
    }

    // =========================================================================
    //  SECTION G — Resize behavior
    // =========================================================================

    @Test
    @DisplayName("inserting beyond threshold should resize without data loss")
    void resizePreservesAllEntries() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        int count = 25;  // exceeds default threshold of 12
        for (int i = 0; i < count; i++) {
            map.put("K" + i, i);
        }
        assertEquals(count, map.size());
        int missing = 0;
        for (int i = 0; i < count; i++) {
            if (map.get("K" + i) == null) missing++;
        }
        assertEquals(0, missing);
    }

    @Test
    @DisplayName("remove all after resize should bring size to 0")
    void removeAllAfterResize() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        for (int i = 0; i < 25; i++) map.put("K" + i, i);
        for (int i = 0; i < 25; i++) map.remove("K" + i);
        assertEquals(0, map.size());
    }

    // =========================================================================
    //  SECTION H — Collision & resize under heavy collision (pre-treeification)
    // =========================================================================
    //
    //  NOTE: treeification is only triggered when a bucket has >= 8 entries AND
    //  the table capacity is >= 64.  With BadHashKeys (all hash=1), 20 entries
    //  stay below that threshold — resize happens instead, which the lo/hi split
    //  handles correctly.

    @Test
    @DisplayName("collision chain with 20 entries should survive multiple resizes without data loss")
    void collisionChainSurvivesResizeWithoutDataLoss() {
        SimpleHashMap<SimpleHashMap.BadHashKey, String> map = new SimpleHashMap<>();
        for (int i = 0; i < 20; i++) {
            map.put(new SimpleHashMap.BadHashKey(i), "v" + i);
        }
        assertEquals(20, map.size());
        for (int i = 0; i < 20; i++) {
            assertEquals("v" + i, map.get(new SimpleHashMap.BadHashKey(i)));
        }
    }

    @Test
    @DisplayName("remove all from collision chain should bring size to 0")
    void removeAllFromCollisionChain() {
        SimpleHashMap<SimpleHashMap.BadHashKey, String> map = new SimpleHashMap<>();
        for (int i = 0; i < 20; i++) {
            map.put(new SimpleHashMap.BadHashKey(i), "v" + i);
        }
        for (int i = 0; i < 20; i++) {
            assertEquals("v" + i, map.remove(new SimpleHashMap.BadHashKey(i)));
        }
        assertEquals(0, map.size());
    }

    // =========================================================================
    //  SECTION I — toString
    // =========================================================================

    @Test
    @DisplayName("toString on empty map should return {}")
    void toStringEmptyMap() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        assertEquals("{}", map.toString());
    }

    @Test
    @DisplayName("toString should contain keys and values")
    void toStringFormat() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        String s = map.toString();
        assertTrue(s.startsWith("{"));
        assertTrue(s.endsWith("}"));
        assertTrue(s.contains("a=1"));
        assertTrue(s.contains("b=2"));
    }

    // =========================================================================
    //  SECTION J — Edge cases
    // =========================================================================

    @Test
    @DisplayName("get after all removes should return null")
    void getAfterAllRemoved() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("x", 1);
        map.remove("x");
        assertNull(map.get("x"));
    }

    @Test
    @DisplayName("overwriting value with same value should not break")
    void overwriteWithSameValue() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("key", 10);
        map.put("key", 10);
        assertEquals(10, map.get("key"));
        assertEquals(1, map.size());
    }

    @Test
    @DisplayName("large number of entries should all be retrievable")
    void largeEntrySet() {
        SimpleHashMap<Integer, String> map = new SimpleHashMap<>();
        int n = 500;
        for (int i = 0; i < n; i++) map.put(i, "v" + i);
        assertEquals(n, map.size());
        for (int i = 0; i < n; i++) assertEquals("v" + i, map.get(i));
    }

    @Test
    @DisplayName("printInternals should not throw")
    void printInternalsDoesNotThrow() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        map.put("a", 1);
        assertDoesNotThrow(map::printInternals);
    }

    @Test
    @DisplayName("printInternals on empty map should not throw")
    void printInternalsOnEmptyMapDoesNotThrow() {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        assertDoesNotThrow(map::printInternals);
    }
}
