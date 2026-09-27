package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The star in front of a nametag, and the FKDR after it.
 *
 * <p>Forge 1.8.9 has no event that hands over the nametag's text to edit in place the way 26.2's
 * mixin does — the vanilla label draw is cancelled and redrawn here instead, with the extra text
 * folded into it. The drawing technique (a camera-facing billboard, written faintly behind
 * everything and then solidly in front of it, which is what gives nametags their see-through-walls
 * look) is unchanged from the working {@code 1.8.9-support} branch's own {@code NametagStars}; only
 * the data source (this module's {@code common} {@link Roster}/{@link StatsService}/{@link
 * ScoutSettings}) is new. The cheat mark above the label is Scout's (it receives this event
 * cancelled).
 */
public final class Nametags {
	private static final float SCALE = 0.02666667f;

	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;

	public Nametags(Roster roster, StatsService stats, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onRenderLabel(RenderLivingEvent.Specials.Pre event) {
		ScoutSettings.Nametag nametag = settings.get().nametag;
		if (!nametag.stars || !roster.isInGame() || !(event.entity instanceof EntityPlayer)) {
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

		String name = player.getName();
		UUID uuid = roster.uuidOf(name);
		if (uuid == null) {
			uuid = player.getGameProfile().getId();
		}

		PlayerStats playerStats = stats.peek(uuid);
		if (playerStats == null) {
			// Nothing known yet: leave vanilla's nametag alone rather than replacing it with a
			// placeholder that flickers for a second at the start of every game.
			if (roster.hasStarted()) {
				stats.request(uuid, name);
			}
			return;
		}

		event.setCanceled(true);
		drawLabel(mc, label(name, playerStats, nametag.fkdr), event.x, event.y + player.height + 0.5, event.z);
	}

	/** The stars in front of the name; Scout draws its own cheat mark above the label. */
	private String label(String name, PlayerStats playerStats, boolean showFkdr) {
		if (playerStats.isNicked()) {
			return "§d[NICK] §f" + name;
		}

		StringBuilder label = new StringBuilder();
		label.append(StatFormat.star(playerStats.getStars())).append(' ').append(name);

		if (showFkdr) {
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
