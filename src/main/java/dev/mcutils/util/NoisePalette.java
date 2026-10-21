package dev.mcutils.util;

import java.util.List;

/**
 * Maps world coordinates to entries of a list through Perlin noise, so neighbouring
 * blocks tend to share an entry and the result looks like a natural, blotchy texture.
 */
public final class NoisePalette<T> {
	/**
	 * Standard deviation of {@link PerlinNoise#sample} with 2 octaves, measured empirically.
	 * Used to spread the (bell-shaped) noise values evenly across the palette.
	 */
	static final double SIGMA = 0.20;

	private final List<T> entries;
	private final PerlinNoise noise;
	private final double invScale;

	/**
	 * @param entries blocks to choose from; repeating an entry makes it more common
	 * @param scale   approximate size of one blotch, in blocks
	 */
	public NoisePalette(List<T> entries, long seed, double scale) {
		if (entries.isEmpty()) {
			throw new IllegalArgumentException("palette must not be empty");
		}
		if (!(scale > 0) || Double.isInfinite(scale)) {
			throw new IllegalArgumentException("scale must be a positive number");
		}
		this.entries = List.copyOf(entries);
		this.noise = new PerlinNoise(seed, 2);
		this.invScale = 1.0 / scale;
	}

	public T pick(int x, int y, int z) {
		// Offset by half a cell: integer lattice points are always exactly 0 in Perlin noise.
		double n = noise.sample((x + 0.5) * invScale, (y + 0.5) * invScale, (z + 0.5) * invScale);
		return entries.get(indexFor(n, entries.size()));
	}

	static int indexFor(double noise, int size) {
		double p = normalCdf(noise / SIGMA);
		int index = (int) (p * size);
		return Math.min(size - 1, Math.max(0, index));
	}

	/** Abramowitz-Stegun 7.1.26 approximation of the standard normal CDF. */
	static double normalCdf(double z) {
		double t = 1.0 / (1.0 + 0.3275911 * Math.abs(z) / Math.sqrt(2));
		double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
		double erf = 1.0 - poly * Math.exp(-z * z / 2);
		return z >= 0 ? 0.5 * (1.0 + erf) : 0.5 * (1.0 - erf);
	}
}
