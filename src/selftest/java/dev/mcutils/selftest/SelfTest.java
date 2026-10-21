package dev.mcutils.selftest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Drives the real commands on a real server with a fake op player and checks the resulting world.
 * Only active with -Dmcutils.selftest=true (set by the gradle "runSelftest" task).
 */
public class SelfTest implements ModInitializer {
	private static final int WARMUP_TICKS = 60;

	private MinecraftServer server;
	private ServerLevel level;
	private ServerPlayer fake;
	private Player wePlayer;
	private LocalSession session;
	private int checks;
	private int ticks;
	private boolean finished;

	@Override
	public void onInitialize() {
		if (!Boolean.getBoolean("mcutils.selftest")) {
			return;
		}
		// Don't start straight from SERVER_STARTED: other mods (WorldEdit fills its block registry there)
		// may not have run their own listeners yet, which made the first command flaky in CI.
		// Real players can't type a command at that moment, so wait a couple of seconds of ticks.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (finished || ++ticks < WARMUP_TICKS) {
				return;
			}
			finished = true;
			try {
				this.server = server;
				run();
				System.out.println("SELFTEST PASS (" + checks + " checks)");
			} catch (Throwable t) {
				System.out.println("SELFTEST FAIL: " + t);
				t.printStackTrace(System.out);
			} finally {
				server.halt(false);
			}
		});
	}

	private void run() {
		level = server.overworld();
		for (int cx = -1; cx <= 5; cx++) {
			for (int cz = -1; cz <= 5; cz++) {
				level.getChunk(cx, cz);
			}
		}
		GameProfile profile = new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000042"), "selftest");
		fake = new LoggingFakePlayer(level, profile);
		server.getPlayerList().op(new NameAndId(profile));
		wePlayer = FabricAdapter.get().fromNativePlayer(fake);
		session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
		session.setBlockChangeLimit(-1);
		System.out.println("SELFTEST fake player op: " + wePlayer.hasPermission("worldedit.region.replace"));
		check(wePlayer.hasPermission("worldedit.region.replace"), "fake player has WorldEdit permission");

		testTexture();
		testPasteEqually();
		testPasteOnGround();
	}

	// ---------------------------------------------------------------- //texture

	private void testTexture() {
		fill(2, 200, 2, 13, 203, 13, Blocks.STONE);
		set(5, 201, 5, Blocks.AIR);
		select(2, 200, 2, 13, 203, 13);

		cmd("//texture stone,andesite,gravel");
		Map<Block, Integer> counts = count(2, 200, 2, 13, 203, 13);
		System.out.println("SELFTEST texture counts: " + counts);
		check(counts.keySet().equals(Set.of(Blocks.STONE, Blocks.ANDESITE, Blocks.GRAVEL, Blocks.AIR)),
			"only palette blocks and the untouched air hole: " + counts);
		check(counts.get(Blocks.AIR) == 1 && at(5, 201, 5) == Blocks.AIR, "air stays air");
		int total = 12 * 4 * 12 - 1;
		for (Block b : new Block[] {Blocks.STONE, Blocks.ANDESITE, Blocks.GRAVEL}) {
			check(counts.get(b) > total * 0.15, "block well represented: " + b + " " + counts.get(b));
		}
		check(neighbourAgreement(2, 200, 2, 12, 4, 12) > 0.45, "texture forms blotches, not static");

		int andesite = counts.get(Blocks.ANDESITE);
		cmd("//texture dirt andesite");
		Map<Block, Integer> afterFilter = count(2, 200, 2, 13, 203, 13);
		check(afterFilter.get(Blocks.DIRT) == andesite && !afterFilter.containsKey(Blocks.ANDESITE),
			"filter block replaced only andesite: " + afterFilter);
		check(afterFilter.get(Blocks.STONE).equals(counts.get(Blocks.STONE)), "stone untouched by filter");

		cmd("//undo");
		check(count(2, 200, 2, 13, 203, 13).equals(counts), "//undo restores the pre-filter texture");

		Map<Block, Integer> before = count(2, 200, 2, 13, 203, 13);
		cmd("//texture not_a_real_block,stone");
		check(count(2, 200, 2, 13, 203, 13).equals(before), "bad block name changes nothing");
		cmd("//texture");
		check(count(2, 200, 2, 13, 203, 13).equals(before), "missing args changes nothing");

		cmd("//texture oak_stairs[facing=north,half=top],stone -s 3");
		Map<Block, Integer> stairs = count(2, 200, 2, 13, 203, 13);
		check(stairs.containsKey(Blocks.OAK_STAIRS) && stairs.containsKey(Blocks.STONE) && stairs.size() == 3,
			"block states with commas parse: " + stairs);
	}

	// ---------------------------------------------------------------- //paste-equaly

	private void testPasteEqually() {
		// A recognisable, non-symmetric source build: stone base, gravel column, one dirt corner.
		fill(2, 200, 2, 13, 203, 13, Blocks.AIR);
		fill(2, 200, 2, 13, 200, 13, Blocks.STONE);
		fill(3, 201, 3, 3, 203, 3, Blocks.GRAVEL);
		set(13, 203, 13, Blocks.DIRT);
		Map<BlockPos, Block> source = snapshot(2, 200, 2, 13, 203, 13);

		stand(8.5, 200, 8.5);
		select(2, 200, 2, 13, 203, 13);
		cmd("//copy");

		stand(40.5, 120, 40.5);
		cmd("//paste-equaly x");
		check(matches(source, 0, -80, 32), "x kept: pasted at original x, player's y/z");

		stand(40.5, 140, 40.5);
		cmd("//paste-equaly yz");
		check(matches(source, 32, 0, 0), "y+z kept: pasted at player's x only");

		fill(2, 200, 2, 13, 203, 13, Blocks.AIR);
		stand(60.5, 130, 60.5);
		cmd("//paste-equaly a");
		check(matches(source, 0, 0, 0), "a keeps everything: pasted back at the original spot");

		fill(70, 50, 70, 90, 70, 90, Blocks.AIR);
		stand(70.5, 60, 70.5);
		cmd("//paste-equaly q");
		cmd("//paste-equaly");
		check(count(70, 50, 70, 90, 70, 90).keySet().equals(Set.of(Blocks.AIR)), "bad/missing axes paste nothing");

		stand(80.5, 60, 80.5);
		cmd("//paste-equally z");
		check(matches(source, 72, -140, 0), "correctly spelled alias works");
	}

	// ---------------------------------------------------------------- //paste-on-ground

	private void testPasteOnGround() {
		Map<BlockPos, Block> source = snapshot(2, 200, 2, 13, 203, 13);
		// Flat ground at y=100 (grass over dirt), a bump up to y=103 in one corner of the footprint.
		fill(55, 90, 55, 95, 99, 95, Blocks.DIRT);
		fill(55, 100, 55, 95, 100, 95, Blocks.GRASS_BLOCK);
		fill(55, 101, 55, 95, 140, 95, Blocks.AIR);
		fill(70, 101, 70, 72, 103, 72, Blocks.DIRT);
		// Leaves above the ground in the player's column must be ignored as "ground".
		set(65, 101, 65, Blocks.OAK_LEAVES);

		stand(65.5, 110, 65.5);
		cmd("//paste-on-ground");
		check(matches(source, 65 - 8, 101 - 200, 65 - 8), "bottom layer sits on the ground, leaves ignored");

		fill(55, 101, 55, 95, 140, 95, Blocks.AIR);
		stand(65.5, 99, 65.5);
		cmd("//paste-on-ground");
		check(matches(source, 65 - 8, 101 - 200, 65 - 8), "starting inside the ground still finds its top");

		fill(55, 101, 55, 95, 140, 95, Blocks.AIR);
		fill(70, 101, 70, 72, 103, 72, Blocks.DIRT);
		stand(65.5, 110, 65.5);
		cmd("//paste-on-ground -f");
		check(matches(source, 65 - 8, 104 - 200, 65 - 8), "-f rests on the highest ground under the footprint");

		fill(55, 104, 55, 95, 140, 95, Blocks.AIR);
		fill(55, 101, 55, 69, 103, 95, Blocks.AIR);
		stand(65.5, 110, 65.5);
		cmd("//flip up");
		cmd("//paste-on-ground");
		// Flipped vertically: the build now hangs *down* from the origin plane, so it must be lifted by its height.
		int lowest = lowestNonAirY(55, 101, 55, 95, 140, 95);
		check(lowest == 101, "flipped build's lowest block is directly on the ground, got y=" + lowest);
	}

	// ---------------------------------------------------------------- helpers

	private void cmd(String command) {
		try {
			// "//texture" is the Brigadier literal "/texture" typed with the chat slash in front.
			int result = server.getCommands().getDispatcher().execute(command.substring(1), fake.createCommandSourceStack());
			System.out.println("SELFTEST cmd " + command + " -> " + result);
		} catch (CommandSyntaxException e) {
			System.out.println("SELFTEST cmd " + command + " -> syntax error: " + e.getMessage());
		}
	}

	/** Prints what the commands tell the player, which a headless fake player would otherwise swallow. */
	private static final class LoggingFakePlayer extends FakePlayer {
		LoggingFakePlayer(ServerLevel level, GameProfile profile) {
			super(level, profile);
		}

		@Override
		public void sendSystemMessage(Component message) {
			System.out.println("SELFTEST   chat: " + message.getString());
		}

		@Override
		public void sendSystemMessage(Component message, boolean overlay) {
			System.out.println("SELFTEST   chat: " + message.getString());
		}
	}

	private void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			throw new AssertionError(what);
		}
		System.out.println("SELFTEST ok: " + what);
	}

	private void stand(double x, double y, double z) {
		fake.teleportTo(x, y, z);
		fake.snapTo(x, y, z);
	}

	private void select(int x1, int y1, int z1, int x2, int y2, int z2) {
		var selector = session.getRegionSelector(wePlayer.getWorld());
		selector.selectPrimary(BlockVector3.at(x1, y1, z1), ActorSelectorLimits.forActor(wePlayer));
		selector.selectSecondary(BlockVector3.at(x2, y2, z2), ActorSelectorLimits.forActor(wePlayer));
	}

	private Block at(int x, int y, int z) {
		return level.getBlockState(new BlockPos(x, y, z)).getBlock();
	}

	private void set(int x, int y, int z, Block block) {
		level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 2);
	}

	private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
		for (int x = x1; x <= x2; x++) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) {
					set(x, y, z, block);
				}
			}
		}
	}

	private Map<Block, Integer> count(int x1, int y1, int z1, int x2, int y2, int z2) {
		Map<Block, Integer> counts = new HashMap<>();
		for (int x = x1; x <= x2; x++) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) {
					counts.merge(at(x, y, z), 1, Integer::sum);
				}
			}
		}
		return counts;
	}

	private Map<BlockPos, Block> snapshot(int x1, int y1, int z1, int x2, int y2, int z2) {
		Map<BlockPos, Block> map = new HashMap<>();
		for (int x = x1; x <= x2; x++) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) {
					map.put(new BlockPos(x, y, z), at(x, y, z));
				}
			}
		}
		return map;
	}

	/** Every snapshot block shows up at its position moved by (dx, dy, dz). */
	private boolean matches(Map<BlockPos, Block> source, int dx, int dy, int dz) {
		int bad = 0;
		for (Map.Entry<BlockPos, Block> e : source.entrySet()) {
			BlockPos p = e.getKey();
			if (at(p.getX() + dx, p.getY() + dy, p.getZ() + dz) != e.getValue()) {
				bad++;
			}
		}
		if (bad > 0) {
			System.out.println("SELFTEST mismatch: " + bad + " of " + source.size() + " blocks, offset " + dx + "," + dy + "," + dz);
		}
		return bad == 0;
	}

	private int lowestNonAirY(int x1, int y1, int z1, int x2, int y2, int z2) {
		for (int y = y1; y <= y2; y++) {
			for (int x = x1; x <= x2; x++) {
				for (int z = z1; z <= z2; z++) {
					if (at(x, y, z) != Blocks.AIR) {
						return y;
					}
				}
			}
		}
		return Integer.MIN_VALUE;
	}

	private double neighbourAgreement(int x0, int y0, int z0, int sx, int sy, int sz) {
		int same = 0;
		int total = 0;
		for (int x = x0; x < x0 + sx - 1; x++) {
			for (int y = y0; y < y0 + sy; y++) {
				for (int z = z0; z < z0 + sz; z++) {
					total++;
					if (at(x, y, z) == at(x + 1, y, z)) {
						same++;
					}
				}
			}
		}
		return same / (double) total;
	}
}
