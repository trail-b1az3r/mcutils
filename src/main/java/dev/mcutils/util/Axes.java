package dev.mcutils.util;

import java.util.Locale;

/** A set of coordinate axes, parsed from input such as {@code x}, {@code xz}, {@code x,z} or {@code a}. */
public record Axes(boolean x, boolean y, boolean z) {

	public static Axes parse(String input) {
		boolean x = false;
		boolean y = false;
		boolean z = false;
		for (char c : input.toLowerCase(Locale.ROOT).toCharArray()) {
			switch (c) {
				case 'x' -> x = true;
				case 'y' -> y = true;
				case 'z' -> z = true;
				case 'a' -> {
					x = true;
					y = true;
					z = true;
				}
				case ',', '+', '&', '/', ' ' -> {
				}
				default -> throw new IllegalArgumentException(
					"Unknown axis '" + c + "'. Use x, y, z, any mix such as xz, or a for all.");
			}
		}
		if (!x && !y && !z) {
			throw new IllegalArgumentException("No axis given. Use x, y, z, any mix such as xz, or a for all.");
		}
		return new Axes(x, y, z);
	}

	@Override
	public String toString() {
		return (x ? "x" : "") + (y ? "y" : "") + (z ? "z" : "");
	}
}
