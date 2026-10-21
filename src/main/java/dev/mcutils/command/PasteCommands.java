package dev.mcutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockCategories;
import com.sk89q.worldedit.world.block.BlockType;
import dev.mcutils.util.ArgSplitter;
import dev.mcutils.util.Axes;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code //paste-equaly <axes> [-a]} and {@code //paste-on-ground [-a] [-f]}.
 * Both reuse the WorldEdit clipboard (rotation/flip included) and edit history.
 */
public final class PasteCommands {
	private static final String PERMISSION = "worldedit.clipboard.paste";
	private static final String EQUALLY_USAGE = "Usage: //paste-equaly <x|y|z|a|mix such as xz> [-a]";
	private static final String GROUND_USAGE = "Usage: //paste-on-ground [-a] [-f]";

	private PasteCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// "equaly" is the requested spelling; the correct spelling works too.
		for (String name : List.of("/paste-equaly", "/paste-equally")) {
			dispatcher.register(Commands.literal(name)
				.executes(context -> CommandSupport.execute(context, PERMISSION, "", PasteCommands::pasteEqually))
				.then(Commands.argument("axes", StringArgumentType.greedyString())
					.suggests(PasteCommands::suggestAxes)
					.executes(context -> CommandSupport.execute(context, PERMISSION,
						StringArgumentType.getString(context, "axes"), PasteCommands::pasteEqually))));
		}
		dispatcher.register(Commands.literal("/paste-on-ground")
			.executes(context -> CommandSupport.execute(context, PERMISSION, "", PasteCommands::pasteOnGround))
			.then(Commands.argument("flags", StringArgumentType.greedyString())
				.executes(context -> CommandSupport.execute(context, PERMISSION,
					StringArgumentType.getString(context, "flags"), PasteCommands::pasteOnGround))));
	}

	/**
	 * Pastes at the player's position, except that the chosen axes are taken from the position the
	 * clipboard was originally copied at. {@code //paste-equaly a} is a paste back at the exact origin.
	 */
	private static void pasteEqually(Player player, LocalSession session, String rawArgs) throws WorldEditException {
		boolean skipAir = false;
		Axes axes = null;
		for (String token : ArgSplitter.tokens(rawArgs)) {
			if (token.equals("-a")) {
				skipAir = true;
			} else if (token.startsWith("-")) {
				throw new CommandSupport.UsageException("Unknown flag " + token + ". " + EQUALLY_USAGE);
			} else if (axes == null) {
				try {
					axes = Axes.parse(token);
				} catch (IllegalArgumentException e) {
					throw new CommandSupport.UsageException(e.getMessage());
				}
			} else {
				throw new CommandSupport.UsageException(EQUALLY_USAGE);
			}
		}
		if (axes == null) {
			throw new CommandSupport.UsageException(EQUALLY_USAGE);
		}

		ClipboardHolder holder = session.getClipboard();
		BlockVector3 origin = holder.getClipboard().getOrigin();
		BlockVector3 here = session.getPlacementPosition(player);
		BlockVector3 to = BlockVector3.at(
			axes.x() ? origin.x() : here.x(),
			axes.y() ? origin.y() : here.y(),
			axes.z() ? origin.z() : here.z());

		paste(player, session, holder, to, skipAir);
		player.printInfo(TextComponent.of("Pasted at " + to + " (original " + axes + " kept)."));
	}

	/**
	 * Pastes at the player's x/z with the bottom of the clipboard resting on the ground there.
	 * Ground is the nearest solid block at or below the player, ignoring leaves, logs and liquids.
	 * With {@code -f} the highest ground under the whole footprint is used, so nothing ends up buried.
	 */
	private static void pasteOnGround(Player player, LocalSession session, String rawArgs) throws WorldEditException {
		boolean skipAir = false;
		boolean fitFootprint = false;
		for (String token : ArgSplitter.tokens(rawArgs)) {
			switch (token) {
				case "-a" -> skipAir = true;
				case "-f" -> fitFootprint = true;
				default -> throw new CommandSupport.UsageException(
					"Unknown argument " + token + ". " + GROUND_USAGE);
			}
		}

		ClipboardHolder holder = session.getClipboard();
		Clipboard clipboard = holder.getClipboard();
		BlockVector3 here = session.getPlacementPosition(player);
		World world = player.getWorld();

		// Footprint and lowest layer of the clipboard as it will actually be pasted (rotation/flip applied).
		Bounds bounds = transformedBounds(clipboard, holder.getTransform());

		int groundY;
		EditSession probe = session.createEditSession(player);
		try {
			Integer ground = fitFootprint
				? highestGroundInFootprint(probe, world, here, bounds)
				: findGround(probe, world, here.x(), here.y(), here.z());
			if (ground == null) {
				throw new CommandSupport.UsageException("Couldn't find any ground below you.");
			}
			groundY = ground;
		} finally {
			probe.close();
		}

		BlockVector3 to = BlockVector3.at(here.x(), groundY + 1 - bounds.minY(), here.z());
		paste(player, session, holder, to, skipAir);
		player.printInfo(TextComponent.of("Pasted on the ground at " + to + "."));
	}

	private static void paste(Player player, LocalSession session, ClipboardHolder holder, BlockVector3 to,
		boolean skipAir) throws WorldEditException {
		EditSession editSession = session.createEditSession(player);
		try {
			Operation operation = holder.createPaste(editSession)
				.to(to)
				.ignoreAirBlocks(skipAir)
				.build();
			Operations.complete(operation);
		} finally {
			editSession.close();
			session.remember(editSession);
		}
	}

	/** Offsets of the pasted clipboard relative to the paste position, after the holder's transform. */
	private record Bounds(int minX, int maxX, int minY, int minZ, int maxZ) {
	}

	private static Bounds transformedBounds(Clipboard clipboard, Transform transform) {
		Region region = clipboard.getRegion();
		BlockVector3 min = region.getMinimumPoint();
		BlockVector3 max = region.getMaximumPoint();
		BlockVector3 origin = clipboard.getOrigin();

		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (int cx : new int[] {min.x(), max.x()}) {
			for (int cy : new int[] {min.y(), max.y()}) {
				for (int cz : new int[] {min.z(), max.z()}) {
					Vector3 offset = transform.apply(BlockVector3.at(cx, cy, cz).subtract(origin).toVector3());
					BlockVector3 block = offset.toBlockPoint();
					minX = Math.min(minX, block.x());
					maxX = Math.max(maxX, block.x());
					minY = Math.min(minY, block.y());
					minZ = Math.min(minZ, block.z());
					maxZ = Math.max(maxZ, block.z());
				}
			}
		}
		return new Bounds(minX, maxX, minY, minZ, maxZ);
	}

	private static Integer highestGroundInFootprint(EditSession editSession, World world, BlockVector3 here,
		Bounds bounds) {
		Integer best = null;
		for (int x = here.x() + bounds.minX(); x <= here.x() + bounds.maxX(); x++) {
			for (int z = here.z() + bounds.minZ(); z <= here.z() + bounds.maxZ(); z++) {
				Integer ground = findGround(editSession, world, x, here.y(), z);
				if (ground != null && (best == null || ground > best)) {
					best = ground;
				}
			}
		}
		return best;
	}

	/**
	 * Y of the top of the ground in this column near {@code startY}: if {@code startY} is inside solid
	 * ground, climb to its top; otherwise search downwards for the first solid block. Starting from the
	 * player's height keeps this correct under ceilings, such as in caves and the Nether.
	 */
	private static Integer findGround(EditSession editSession, World world, int x, int startY, int z) {
		int minY = world.getMinY();
		int maxY = world.getMaxY();
		int y = Math.max(minY, Math.min(maxY, startY));
		if (isGround(editSession, x, y, z)) {
			while (y < maxY && isGround(editSession, x, y + 1, z)) {
				y++;
			}
			return y;
		}
		for (y--; y >= minY; y--) {
			if (isGround(editSession, x, y, z)) {
				return y;
			}
		}
		return null;
	}

	private static boolean isGround(EditSession editSession, int x, int y, int z) {
		BlockType type = editSession.getBlock(BlockVector3.at(x, y, z)).getBlockType();
		return type.getMaterial().isSolid()
			&& !type.getMaterial().isLiquid()
			&& !BlockCategories.LEAVES.contains(type)
			&& !BlockCategories.LOGS.contains(type);
	}

	private static CompletableFuture<Suggestions> suggestAxes(
		com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		String remaining = builder.getRemaining();
		if (remaining.contains(" ")) {
			return builder.buildFuture();
		}
		for (String option : List.of("x", "y", "z", "xy", "xz", "yz", "a")) {
			if (option.startsWith(remaining.toLowerCase())) {
				builder.suggest(option);
			}
		}
		return builder.buildFuture();
	}
}
