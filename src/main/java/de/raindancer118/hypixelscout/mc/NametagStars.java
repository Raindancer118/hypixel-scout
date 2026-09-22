package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.UUID;

/**
 * The star above a player's head, in place of their plain nametag.
 *
 * <p>Forge has no event that hands over the nametag's text, so the vanilla label is cancelled and
 * drawn again here with the star in front of it. The drawing is vanilla's own: a billboard turned
 * to face the camera, written once behind everything and once in front, which is what gives
 * nametags their see-through-walls look.
 */
public final class NametagStars {
	private static final float SCALE = 0.02666667f;

	private final StatsService stats;
	private final ScoutConfig config;
	private final RosterTracker roster;

	public NametagStars(StatsService stats, ScoutConfig config, RosterTracker roster) {
		this.stats = stats;
		this.config = config;
		this.roster = roster;
	}

	@SubscribeEvent
	public void onRenderLabel(RenderLivingEvent.Specials.Pre<?> event) {
		if (!config.isNametagStars() || !roster.isInBedwars()) {
			return;
		}

		if (!(event.entity instanceof EntityPlayer)) {
			return;
		}

		EntityPlayer player = (EntityPlayer) event.entity;
		Minecraft mc = Minecraft.getMinecraft();
		if (player == mc.thePlayer) {
			return;
		}

		// Vanilla only labels a player who is close enough and not sneaking; keeping to the same
		// rule means this does not suddenly start labelling people vanilla would have hidden.
		if (player.isSneaking() || player.isInvisibleToPlayer(mc.thePlayer)) {
			return;
		}

		UUID uuid = roster.uuidOf(player.getName());
		if (uuid == null) {
			uuid = player.getGameProfile().getId();
		}

		PlayerStats playerStats = stats.peek(uuid);
		if (playerStats == null) {
			// Nothing known yet: leave vanilla's nametag alone rather than replacing it with a
			// placeholder that flickers for a second at the start of every game.
			stats.request(uuid, player.getName());
			return;
		}

		event.setCanceled(true);
		drawLabel(mc, label(player.getName(), playerStats), event.x, event.y + player.height + 0.5,
				event.z);
	}

	private String label(String name, PlayerStats playerStats) {
		if (playerStats.isNicked()) {
			return "§d[NICK] §f" + name;
		}

		StringBuilder label = new StringBuilder();
		label.append(StatFormat.star(playerStats.getStars())).append(' ');
		label.append(StatsLines.rankColour(playerStats.getRank())).append(name);

		if (config.isNametagFkdr()) {
			label.append(' ').append(StatFormat.ratioColour(playerStats.getFkdr()))
					.append(StatFormat.ratio(playerStats.getFkdr()));
		}

		return label.toString();
	}

	/** Vanilla's own label drawing, with the text this mod wants in it. */
	private void drawLabel(Minecraft mc, String text, double x, double y, double z) {
		RenderManager manager = mc.getRenderManager();
		FontRenderer font = mc.fontRendererObj;

		GlStateManager.pushMatrix();
		GlStateManager.translate((float) x, (float) y, (float) z);
		GL11.glNormal3f(0.0f, 1.0f, 0.0f);
		GlStateManager.rotate(-manager.playerViewY, 0.0f, 1.0f, 0.0f);
		GlStateManager.rotate(manager.options.thirdPersonView == 2 ? -manager.playerViewX
				: manager.playerViewX, 1.0f, 0.0f, 0.0f);
		GlStateManager.scale(-SCALE, -SCALE, SCALE);
		GlStateManager.disableLighting();
		GlStateManager.depthMask(false);
		GlStateManager.disableDepth();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

		int half = font.getStringWidth(text) / 2;

		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer renderer = tessellator.getWorldRenderer();
		GlStateManager.disableTexture2D();
		renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
		renderer.pos(-half - 1, -1, 0.0).color(0.0f, 0.0f, 0.0f, 0.25f).endVertex();
		renderer.pos(-half - 1, 8, 0.0).color(0.0f, 0.0f, 0.0f, 0.25f).endVertex();
		renderer.pos(half + 1, 8, 0.0).color(0.0f, 0.0f, 0.0f, 0.25f).endVertex();
		renderer.pos(half + 1, -1, 0.0).color(0.0f, 0.0f, 0.0f, 0.25f).endVertex();
		tessellator.draw();
		GlStateManager.enableTexture2D();

		// Once faint through the walls, once solid in front of them: the two passes are what make
		// a nametag readable from anywhere without hiding the world behind it.
		font.drawString(text, -half, 0, 0x20FFFFFF);
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		font.drawString(text, -half, 0, -1);

		GlStateManager.enableLighting();
		GlStateManager.disableBlend();
		GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
		GlStateManager.popMatrix();
	}
}
