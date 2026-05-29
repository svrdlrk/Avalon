package com.avalon.dnd.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapBattleRulesGeometrySupportTest {

    @Test
    void firstNonNullReturnsFirstAvailableValue() {
        assertEquals("a", MapBattleRulesGeometrySupport.firstNonNull(null, "a", "b"));
        assertNull(MapBattleRulesGeometrySupport.firstNonNull(null, null));
    }

    @Test
    void normalizeFacingAngleWrapsIntoExpectedRange() {
        assertEquals(10, MapBattleRulesGeometrySupport.normalizeFacingAngleDeg(370));
        assertEquals(-170, MapBattleRulesGeometrySupport.normalizeFacingAngleDeg(190));
        assertEquals(180, MapBattleRulesGeometrySupport.normalizeFacingAngleDeg(540));
    }

    @Test
    void mergeVisibleCellsCombinesDifferentShapes() {
        boolean[][] left = { { true, false }, null };
        boolean[][] right = { { false, true, true }, { true } };

        boolean[][] merged = MapBattleRulesGeometrySupport.mergeVisibleCells(left, right);

        assertEquals(2, merged.length);
        assertEquals(3, merged[0].length);
        assertTrue(merged[0][0]);
        assertTrue(merged[0][1]);
        assertTrue(merged[0][2]);
        assertTrue(merged[1][0]);
    }

    @Test
    void readHelpersHandleTextAndNumbers() {
        assertTrue(MapBattleRulesGeometrySupport.readBoolean(Boolean.TRUE, false));
        assertTrue(MapBattleRulesGeometrySupport.readBoolean("yes", false));
        assertEquals(12, MapBattleRulesGeometrySupport.readInt("12", 5));
        assertEquals(2.5, MapBattleRulesGeometrySupport.readDouble("2.5", 1.0), 0.0001);
    }
}
