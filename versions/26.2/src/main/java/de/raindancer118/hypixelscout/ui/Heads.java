package de.raindancer118.hypixelscout.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player's face, from their skin.
 *
 * <p>Anybody in the tab list has their skin on the client already. Anybody else — a stranger looked
 * up by name — is resolved the way a player head item is: once, in the background, through the
 * game's own skin cache. Until it arrives the default face stands in, which is still a face.
 */
public final class Heads {
	/** One profile per player, so the skin cache sees the same key every frame and resolves once. */
	private static final Map<UUID, ResolvableProfile> STRANGERS = new ConcurrentHashMap<>();

	/** The same, for names typed into the lookup before anybody knows their UUID. */
	private static final Map<String, ResolvableProfile> BY_NAME = new ConcurrentHashMap<>();

	private Heads() {
	}

	/** A face by name alone, for the recent lookups. */
	public static void drawByName(GuiGraphicsExtractor g, String name, int x, int y, int size) {
		if (name == null || name.isBlank()) {
			return;
		}

		ResolvableProfile profile = BY_NAME.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT),
				ResolvableProfile::createUnresolved);
		PlayerFaceExtractor.extractRenderState(g, profile, x, y, size);
	}

	public static void draw(GuiGraphicsExtractor g, UUID uuid, int x, int y, int size) {
		if (uuid == null) {
			return;
		}

		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);

		if (info != null) {
			PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x, y, size);
			return;
		}

		ResolvableProfile profile = STRANGERS.computeIfAbsent(uuid, ResolvableProfile::createUnresolved);
		PlayerFaceExtractor.extractRenderState(g, profile, x, y, size);
	}
}
