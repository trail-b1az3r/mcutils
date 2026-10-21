package dev.mcutils.command;

import com.mojang.brigadier.context.CommandContext;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import net.minecraft.commands.CommandSourceStack;

/** Glue between Brigadier and WorldEdit: resolves the player, checks permission, reports errors. */
final class CommandSupport {
	private CommandSupport() {
	}

	@FunctionalInterface
	interface Action {
		void run(Player player, LocalSession session, String args) throws WorldEditException;
	}

	static int execute(CommandContext<CommandSourceStack> context, String permission, String args, Action action) {
		Actor actor = FabricAdapter.get().adaptCommandSource(context.getSource());
		if (!(actor instanceof Player player)) {
			actor.printError(TextComponent.of("This command can only be used by a player."));
			return 0;
		}
		if (!actor.hasPermission(permission)) {
			actor.printError(TextComponent.of("You don't have permission to do that."));
			return 0;
		}

		LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
		try {
			action.run(player, session, args);
			return 1;
		} catch (UsageException e) {
			actor.printError(TextComponent.of(e.getMessage()));
			return 0;
		} catch (IncompleteRegionException e) {
			actor.printError(TextComponent.of("Make a region selection first."));
			return 0;
		} catch (EmptyClipboardException e) {
			actor.printError(TextComponent.of("Your clipboard is empty. Use //copy first."));
			return 0;
		} catch (MaxChangedBlocksException e) {
			actor.printError(TextComponent.of("Block change limit reached (" + e.getBlockLimit()
				+ "). The blocks changed so far can be undone with //undo."));
			return 0;
		} catch (WorldEditException e) {
			actor.printError(TextComponent.of("WorldEdit error: " + e.getMessage()));
			return 0;
		}
	}

	/** A problem with what the user typed. The message is shown to them as-is. */
	static final class UsageException extends RuntimeException {
		UsageException(String message) {
			super(message);
		}
	}
}
