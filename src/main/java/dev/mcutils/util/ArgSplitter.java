package dev.mcutils.util;

import java.util.ArrayList;
import java.util.List;

/** Splitting helpers that respect block-state brackets, e.g. {@code oak_stairs[facing=north,half=top]}. */
public final class ArgSplitter {
	private ArgSplitter() {
	}

	/** Splits on whitespace that is not inside {@code [...]} or {@code {...}}. */
	public static List<String> tokens(String input) {
		return split(input, true, ' ');
	}

	/** Splits on commas that are not inside {@code [...]} or {@code {...}}. */
	public static List<String> commaList(String input) {
		return split(input, false, ',');
	}

	private static List<String> split(String input, boolean whitespace, char separator) {
		List<String> out = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int depth = 0;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '[' || c == '{') {
				depth++;
			} else if ((c == ']' || c == '}') && depth > 0) {
				depth--;
			}
			boolean isSeparator = depth == 0 && (whitespace ? Character.isWhitespace(c) : c == separator);
			if (isSeparator) {
				flush(out, current);
			} else {
				current.append(c);
			}
		}
		flush(out, current);
		return out;
	}

	private static void flush(List<String> out, StringBuilder current) {
		if (!current.isEmpty()) {
			out.add(current.toString());
			current.setLength(0);
		}
	}
}
