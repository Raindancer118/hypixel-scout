package de.raindancer118.hypixelscout.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A player's face, from their skin.
 *
 * <p>26.2 resolves a stranger's skin through the game's own async skin cache
 * ({@code ResolvableProfile}/{@code PlayerFaceExtractor}), which 1.8.9 has no equivalent of. Here,
 * anybody in the tab list — the only source of a real, already-downloaded skin on this version —
 * is looked up straight from {@link NetworkPlayerInfo#getLocationSkin()}; anybody else falls back
 * to the deterministic default skin ({@link DefaultPlayerSkin#getDefaultSkin}, Steve or Alex by the
 * UUID's parity) rather than waiting on a network request that this old client has no clean hook to
 * kick off outside the tab list. Once the real skin arrives in the tab list it is used automatically
 * on the next frame — nothing here caches a stale texture.
 */
public final class Heads {
	private Heads() {
	}

	/** A face by UUID: the real skin if the tab list has it, the default one otherwise. */
	public static void draw(UUID uuid, int x, int y, int size) {
		if (uuid == null) {
			return;
		}

		draw(skinOf(uuid), x, y, size);
	}

	/** A face by name alone, for the recent lookups; the client only knows a UUID via the tab list. */
	public static void drawByName(String name, int x, int y, int size) {
		if (name == null || name.trim().isEmpty()) {
			return;
		}

		NetHandlerPlayClient connection = Minecraft.getMinecraft().getNetHandler();
		NetworkPlayerInfo info = connection == null ? null : connection.getPlayerInfo(name);
		ResourceLocation skin = info != null && info.getLocationSkin() != null ? info.getLocationSkin()
				: DefaultPlayerSkin.getDefaultSkin(offlineUuid(name));
		draw(skin, x, y, size);
	}

	private static ResourceLocation skinOf(UUID uuid) {
		NetHandlerPlayClient connection = Minecraft.getMinecraft().getNetHandler();
		NetworkPlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);
		return info != null && info.getLocationSkin() != null ? info.getLocationSkin()
				: DefaultPlayerSkin.getDefaultSkin(uuid);
	}

	/** A stable stand-in UUID for a bare name, only ever used to pick which default skin to show. */
	private static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * The 8x8 face at texture UV (8,8) and the 8x8 hat overlay at UV (40,8), scaled up to
	 * {@code size}, on a 64x64 skin texture — the classic 1.8.9 head-drawing technique, unchanged
	 * from the working {@code 1.8.9-support} branch's {@code ScoutTheme.head}.
	 */
	private static void draw(ResourceLocation skin, int x, int y, int size) {
		Minecraft mc = Minecraft.getMinecraft();
		GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
		mc.getTextureManager().bindTexture(skin);
		Gui.drawScaledCustomSizeModalRect(x, y, 8.0f, 8.0f, 8, 8, size, size, 64.0f, 64.0f);
		Gui.drawScaledCustomSizeModalRect(x, y, 40.0f, 8.0f, 8, 8, size, size, 64.0f, 64.0f);
	}
}
