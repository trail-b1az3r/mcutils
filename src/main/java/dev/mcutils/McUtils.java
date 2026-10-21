package dev.mcutils;

import dev.mcutils.command.PasteCommands;
import dev.mcutils.command.TextureCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class McUtils implements ModInitializer {
	public static final String MOD_ID = "mcutils";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			TextureCommand.register(dispatcher);
			PasteCommands.register(dispatcher);
		});
	}
}
