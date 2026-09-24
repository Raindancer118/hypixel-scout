package de.raindancer118.hypixelscout.startup;

import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * A bare-bones {@link ICommandSender} for running {@code /scout} through {@link
 * net.minecraftforge.client.ClientCommandHandler} in the startup test, where there is no
 * singleplayer world (the test only ever reaches the main menu) and so no real player entity to
 * send the command as. Every subcommand this test exercises only reads mod state ({@code
 * HypixelScout}'s settings/roster/client), never the world, so the world-shaped methods below are
 * safe to answer with harmless defaults — nothing under test calls them.
 */
final class TestCommandSender implements ICommandSender {
	private final List<String> messages = new ArrayList<String>();

	@Override
	public String getName() {
		return "StartupTest";
	}

	@Override
	public IChatComponent getDisplayName() {
		return new ChatComponentText(getName());
	}

	@Override
	public void addChatMessage(IChatComponent component) {
		messages.add(component.getUnformattedText());
	}

	@Override
	public boolean canCommandSenderUseCommand(int permissionLevel, String commandName) {
		return true;
	}

	@Override
	public BlockPos getPosition() {
		return BlockPos.ORIGIN;
	}

	@Override
	public Vec3 getPositionVector() {
		return new Vec3(0, 0, 0);
	}

	@Override
	public World getEntityWorld() {
		return null;
	}

	@Override
	public Entity getCommandSenderEntity() {
		return null;
	}

	@Override
	public boolean sendCommandFeedback() {
		return true;
	}

	@Override
	public void setCommandStat(CommandResultStats.Type type, int amount) {
		// Nothing to track here — the test only reads the chat feedback, not the score stats.
	}

	/** Every line this sender has been sent so far, oldest first. */
	List<String> messages() {
		return messages;
	}
}
