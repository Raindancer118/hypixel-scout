package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.ui.Column;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.PlayerRow;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * The tab list, with stats in it, in place of vanilla's — during a Bedwars game and only when
 * switched on. Everywhere else the vanilla list is drawn exactly as before.
 *
 * <p>Cancels {@link RenderGameOverlayEvent.Pre} for {@code PLAYER_LIST} and draws this instead of a
 * mixin into {@code GuiPlayerTabOverlay}: the whole layout is this class's to decide, and there is
 * no coremod to keep working across Forge builds. Ported from 26.2's {@code ui.hud.TabStatsElement}
 * (there a {@code HudElementRegistry.replaceElement}), the same technique the old
 * {@code 1.8.9-support} branch's {@code TabStatsOverlay} already used successfully.
 */
public final class TabStatsElement {
	private static final int PADDING = 6;
	private static final int ROW = 12;
	private static final int HEAD = 8;
	private static final int GAP = 10;

	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;

	public TabStatsElement(Roster roster, StatsService stats, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
	}

	@SubscribeEvent
	public void onRenderPlayerList(RenderGameOverlayEvent.Pre event) {
		if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST) {
			return;
		}

		ScoutSettings current = settings.get();
		if (!current.tab.enabled || !roster.isInGame() || roster.members().isEmpty()) {
			// Vanilla's own list draws itself; nothing to cancel.
			return;
		}

		Minecraft client = Minecraft.getMinecraft();
		if (client.thePlayer == null || !client.gameSettings.keyBindPlayerList.isKeyDown()
				|| client.currentScreen != null) {
			return;
		}

		event.setCanceled(true);
		draw(new ScaledResolution(client), current);
	}

	private void draw(ScaledResolution resolution, ScoutSettings current) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;

		// Teams together, strongest first inside each, whatever the table is sorted by.
		List<PlayerRow> rows = new ArrayList<PlayerRow>(PlayerRow.all(roster, stats, current, SortMode.STARS, 100));
		Collections.sort(rows, new Comparator<PlayerRow>() {
			@Override
			public int compare(PlayerRow left, PlayerRow right) {
				int byTeam = left.team().name().compareTo(right.team().name());
				return byTeam != 0 ? byTeam : PlayerRow.by(SortMode.STARS).compare(left, right);
			}
		});

		List<Column> columns = Column.compact(current);
		int nameWidth = 0;
		int[] widths = new int[columns.size()];
		for (int i = 0; i < columns.size(); i++) {
			widths[i] = ScoutTheme.width(font, StatCollector.translateToLocal(columns.get(i).translationKey()));
		}

		for (PlayerRow row : rows) {
			nameWidth = Math.max(nameWidth, ScoutTheme.width(font, row.displayName()));
			for (int i = 0; i < columns.size(); i++) {
				widths[i] = Math.max(widths[i], ScoutTheme.width(font, columns.get(i).text(row)));
			}
		}

		int content = 2 + 3 + HEAD + 4 + nameWidth;
		for (int w : widths) {
			content += GAP + w;
		}

		int width = content + PADDING * 2;
		int height = ScoutTheme.HEADER_HEIGHT + PADDING + 11 + rows.size() * ROW + PADDING - 2;

		// One column, shrunk until it fits: a list split in two panes reads worse than a smaller one.
		float scale = Math.min(1.0f, Math.min((resolution.getScaledWidth() - 8) / (float) width,
				(resolution.getScaledHeight() - 14) / (float) height));
		scale = Math.max(0.5f, scale);

		int left = Math.round((resolution.getScaledWidth() / scale - width) / 2);
		int top = Math.round(6 / scale);

		GlStateManager.pushMatrix();
		GlStateManager.scale(scale, scale, 1.0F);

		ScoutTheme.panel(left, top, width, height, 92);
		ScoutTheme.header(left, top, width, 92);
		ScoutTheme.text(font, ScoutTheme.accentCode() + "§lSCOUT §8· §7"
				+ StatCollector.translateToLocal("message.hypixelscout.tab.title"), left + PADDING, top + 6,
				ScoutTheme.TEXT);
		ScoutTheme.textRight(font, "§7" + StatCollector.translateToLocalFormatted("message.hypixelscout.table.players",
				rows.size()), left + width - PADDING, top + 6, ScoutTheme.TEXT);

		int x = left + PADDING;
		int right = x + content;
		int y = top + ScoutTheme.HEADER_HEIGHT + PADDING;

		int edge = right;
		int[] rights = new int[columns.size()];
		for (int i = columns.size() - 1; i >= 0; i--) {
			rights[i] = edge;
			edge -= widths[i] + GAP;
		}

		ScoutTheme.text(font, StatCollector.translateToLocal("message.hypixelscout.column.player"),
				x + 2 + 3 + HEAD + 4, y, ScoutTheme.TEXT_FAINT);
		for (int i = 0; i < columns.size(); i++) {
			ScoutTheme.textRight(font, StatCollector.translateToLocal(columns.get(i).translationKey()), rights[i], y,
					ScoutTheme.TEXT_FAINT);
		}
		y += 9;
		ScoutTheme.divider(x, y, content);
		y += 2;

		String previousTeam = null;
		for (int index = 0; index < rows.size(); index++) {
			PlayerRow row = rows.get(index);
			if (previousTeam != null && !previousTeam.equals(row.team().name())) {
				ScoutTheme.divider(x, y, content);
			}
			previousTeam = row.team().name();

			if (index % 2 == 1) {
				Gui.drawRect(x, y, right, y + ROW, ScoutTheme.STRIPE);
			}

			ScoutTheme.pill(x, y + 2, 2, ROW - 4, row.team().argb());
			Heads.draw(row.uuid(), x + 5, y + 2, HEAD);
			ScoutTheme.text(font, row.displayName(), x + 2 + 3 + HEAD + 4, y + 2, ScoutTheme.TEXT);
			for (int i = 0; i < columns.size(); i++) {
				ScoutTheme.textRight(font, columns.get(i).text(row), rights[i], y + 2, ScoutTheme.TEXT);
			}

			y += ROW;
		}

		GlStateManager.popMatrix();
	}
}
