package dev.mcutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import dev.mcutils.util.ArgSplitter;
import dev.mcutils.util.NoisePalette;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code //texture <block,block,...> [filter] [-s size]}: re-textures the WorldEdit selection
 * with the given blocks, distributed by 3D Perlin noise.
 */
public final class TextureCommand {
	private static final String PERMISSION = "worldedit.region.replace";
	private static final double DEFAULT_SIZE = 6;
	private static final String USAGE = "Usage: //texture <block,block,...> [only-this-block] [-s size]";

	private TextureCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("/texture")
			.executes(context -> CommandSupport.execute(context, PERMISSION, "", TextureCommand::run))
			.then(Commands.argument("blocks", StringArgumentType.greedyString())
				.suggests(TextureCommand::suggestBlocks)
				.executes(context -> CommandSupport.execute(context, PERMISSION,
					StringArgumentType.getString(context, "blocks"), TextureCommand::run))));
	}

	private static void run(Player player, LocalSession session, String rawArgs) throws WorldEditException {
		List<String> positional = new ArrayList<>();
		double size = DEFAULT_SIZE;
		List<String> tokens = ArgSplitter.tokens(rawArgs);
		for (int i = 0; i < tokens.size(); i++) {
			String token = tokens.get(i);
			if (token.equals("-s")) {
				if (++i >= tokens.size()) {
					throw new CommandSupport.UsageException("-s needs a size, e.g. -s 8");
				}
				size = parseSize(tokens.get(i));
			} else if (token.startsWith("-") && token.length() > 1 && !token.contains(":")) {
				throw new CommandSupport.UsageException("Unknown flag " + token + ". " + USAGE);
			} else {
				positional.add(token);
			}
		}
		if (positional.isEmpty() || positional.size() > 2) {
			throw new CommandSupport.UsageException(USAGE);
		}

		Region region = session.getSelection(player.getWorld());
		long seed = ThreadLocalRandom.current().nextLong();

		int changed = 0;
		int distinct;
		EditSession editSession = session.createEditSession(player);
		try {
			ParserContext parserContext = new ParserContext();
			parserContext.setActor(player);
			parserContext.setWorld(player.getWorld());
			parserContext.setSession(session);
			parserContext.setExtent(editSession);
			parserContext.setRestricted(true);
			parserContext.setPreferringWildcard(false);
			parserContext.setTryLegacy(false);

			List<BaseBlock> palette = parseBlocks(positional.get(0), parserContext);
			distinct = (int) palette.stream().distinct().count();
			Mask mask = positional.size() == 2
				? new BlockMask(editSession, parseBlocks(positional.get(1), parserContext))
				: new ExistingBlockMask(editSession);

			NoisePalette<BaseBlock> noise = new NoisePalette<>(palette, seed, size);
			for (BlockVector3 position : region) {
				if (!mask.test(position)) {
					continue;
				}
				BaseBlock block = noise.pick(position.x(), position.y(), position.z());
				if (editSession.setBlock(position, block)) {
					changed++;
				}
			}
		} finally {
			editSession.close();
			session.remember(editSession);
		}

		player.printInfo(TextComponent.of("Textured " + changed + " block" + (changed == 1 ? "" : "s")
			+ " with " + distinct + " block type" + (distinct == 1 ? "" : "s") + " (noise size " + size + ")."));
	}

	private static double parseSize(String text) {
		try {
			double size = Double.parseDouble(text);
			if (size >= 1 && size <= 1024) {
				return size;
			}
		} catch (NumberFormatException ignored) {
		}
		throw new CommandSupport.UsageException("Noise size must be a number between 1 and 1024.");
	}

	private static List<BaseBlock> parseBlocks(String list, ParserContext context) {
		List<BaseBlock> blocks = new ArrayList<>();
		for (String entry : ArgSplitter.commaList(list)) {
			try {
				blocks.add(WorldEdit.getInstance().getBlockFactory().parseFromInput(entry, context));
			} catch (InputParseException e) {
				throw new CommandSupport.UsageException("Can't use '" + entry + "': " + e.getMessage());
			}
		}
		if (blocks.isEmpty()) {
			throw new CommandSupport.UsageException(USAGE);
		}
		return blocks;
	}

	private static CompletableFuture<Suggestions> suggestBlocks(
		com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		String remaining = builder.getRemaining();
		int cut = 0;
		int depth = 0;
		for (int i = 0; i < remaining.length(); i++) {
			char c = remaining.charAt(i);
			if (c == '[' || c == '{') {
				depth++;
			} else if (c == ']' || c == '}') {
				depth = Math.max(0, depth - 1);
			} else if (depth == 0 && (c == ',' || Character.isWhitespace(c))) {
				cut = i + 1;
			}
		}
		if (depth > 0) {
			return builder.buildFuture();
		}
		SuggestionsBuilder word = builder.createOffset(builder.getStart() + cut);
		String prefix = word.getRemaining().toLowerCase(Locale.ROOT);
		for (BlockType type : BlockType.REGISTRY.values()) {
			String id = type.id();
			String shortId = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
			if (shortId.startsWith(prefix) || id.startsWith(prefix)) {
				word.suggest(shortId);
			}
		}
		return word.buildFuture();
	}
}
