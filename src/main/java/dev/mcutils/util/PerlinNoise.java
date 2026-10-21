package dev.mcutils.util;

import java.util.Random;

/**
 * Seeded 3D Perlin noise (Ken Perlin's "improved noise") with optional fractal octaves.
 */
public final class PerlinNoise {
	private final int[] perm = new int[512];
	private final int octaves;

	public PerlinNoise(long seed, int octaves) {
		if (octaves < 1) {
			throw new IllegalArgumentException("octaves must be >= 1");
		}
		this.octaves = octaves;

		int[] p = new int[256];
		for (int i = 0; i < 256; i++) {
			p[i] = i;
		}
		Random random = new Random(seed);
		for (int i = 255; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int tmp = p[i];
			p[i] = p[j];
			p[j] = tmp;
		}
		for (int i = 0; i < 512; i++) {
			perm[i] = p[i & 255];
		}
	}

	/** Fractal noise, roughly in [-1, 1] and centred on 0. */
	public double sample(double x, double y, double z) {
		double sum = 0;
		double amplitude = 1;
		double frequency = 1;
		double total = 0;
		for (int i = 0; i < octaves; i++) {
			sum += single(x * frequency, y * frequency, z * frequency) * amplitude;
			total += amplitude;
			amplitude *= 0.5;
			frequency *= 2;
		}
		return sum / total;
	}

	private double single(double x, double y, double z) {
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		int zi = (int) Math.floor(z);
		x -= xi;
		y -= yi;
		z -= zi;
		xi &= 255;
		yi &= 255;
		zi &= 255;

		double u = fade(x);
		double v = fade(y);
		double w = fade(z);

		int a = perm[xi] + yi;
		int aa = perm[a] + zi;
		int ab = perm[a + 1] + zi;
		int b = perm[xi + 1] + yi;
		int ba = perm[b] + zi;
		int bb = perm[b + 1] + zi;

		return lerp(w,
			lerp(v,
				lerp(u, grad(perm[aa], x, y, z), grad(perm[ba], x - 1, y, z)),
				lerp(u, grad(perm[ab], x, y - 1, z), grad(perm[bb], x - 1, y - 1, z))),
			lerp(v,
				lerp(u, grad(perm[aa + 1], x, y, z - 1), grad(perm[ba + 1], x - 1, y, z - 1)),
				lerp(u, grad(perm[ab + 1], x, y - 1, z - 1), grad(perm[bb + 1], x - 1, y - 1, z - 1))));
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}

	private static double grad(int hash, double x, double y, double z) {
		int h = hash & 15;
		double u = h < 8 ? x : y;
		double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
		return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
	}
}
