package dev.mcutils.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UtilTest {
	@Test
	void axesParseSingleMixedAndAll() {
		assertEquals(new Axes(true, false, false), Axes.parse("x"));
		assertEquals(new Axes(true, false, true), Axes.parse("XZ"));
		assertEquals(new Axes(false, true, true), Axes.parse("y,z"));
		assertEquals(new Axes(true, true, true), Axes.parse("a"));
		assertThrows(IllegalArgumentException.class, () -> Axes.parse("q"));
		assertThrows(IllegalArgumentException.class, () -> Axes.parse(","));
	}

	@Test
	void commaListKeepsBlockStatesTogether() {
		assertEquals(List.of("stone", "oak_stairs[facing=north,half=top]", "dirt"),
			ArgSplitter.commaList("stone,oak_stairs[facing=north,half=top],dirt"));
		assertEquals(List.of("stone", "stone"), ArgSplitter.commaList("stone,,stone,"));
	}

	@Test
	void tokensSplitOnWhitespaceOutsideBrackets() {
		assertEquals(List.of("a,b", "oak_stairs[half=top, facing=north]", "-s", "8"),
			ArgSplitter.tokens("  a,b   oak_stairs[half=top, facing=north] -s 8 "));
	}

	@Test
	void noiseIsDeterministicAndSeedDependent() {
		PerlinNoise a = new PerlinNoise(42, 2);
		PerlinNoise b = new PerlinNoise(42, 2);
		PerlinNoise c = new PerlinNoise(43, 2);
		boolean differs = false;
		for (int i = 0; i < 50; i++) {
			double v = a.sample(i * 0.37, i * 0.11, i * 0.53);
			assertEquals(v, b.sample(i * 0.37, i * 0.11, i * 0.53));
			assertTrue(v >= -1 && v <= 1);
			differs |= v != c.sample(i * 0.37, i * 0.11, i * 0.53);
		}
		assertTrue(differs);
	}

	@Test
	void noiseIsSmooth() {
		PerlinNoise n = new PerlinNoise(7, 2);
		for (int i = 0; i < 200; i++) {
			double x = i * 0.31;
			assertTrue(Math.abs(n.sample(x, 1.3, 2.7) - n.sample(x + 0.01, 1.3, 2.7)) < 0.05);
		}
	}

	@Test
	void paletteSpreadsEvenlyAndFormsClusters() {
		List<String> blocks = List.of("a", "b", "c", "d");
		NoisePalette<String> palette = new NoisePalette<>(blocks, 1234, 6);
		int[] counts = new int[4];
		int total = 0;
		int sameAsNeighbour = 0;
		for (int x = 0; x < 64; x++) {
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 64; z++) {
					String p = palette.pick(x, y, z);
					counts[blocks.indexOf(p)]++;
					total++;
					if (p.equals(palette.pick(x + 1, y, z))) {
						sameAsNeighbour++;
					}
				}
			}
		}
		for (int count : counts) {
			double share = count / (double) total;
			assertTrue(share > 0.15 && share < 0.35, "uneven distribution: " + java.util.Arrays.toString(counts));
		}
		assertTrue(sameAsNeighbour / (double) total > 0.5, "should form blotches (random would be 0.25)");
	}

	@Test
	void paletteRejectsBadInput() {
		assertThrows(IllegalArgumentException.class, () -> new NoisePalette<>(List.<String>of(), 1, 4));
		assertThrows(IllegalArgumentException.class, () -> new NoisePalette<>(List.of("a"), 1, 0));
		assertFalse(new NoisePalette<>(List.of("a"), 1, 4).pick(1, 2, 3).isEmpty());
	}

	@Test
	void normalCdfIsSane() {
		assertEquals(0.5, NoisePalette.normalCdf(0), 1e-6);
		assertEquals(0.8413, NoisePalette.normalCdf(1), 1e-3);
		assertEquals(0.1587, NoisePalette.normalCdf(-1), 1e-3);
	}
}
