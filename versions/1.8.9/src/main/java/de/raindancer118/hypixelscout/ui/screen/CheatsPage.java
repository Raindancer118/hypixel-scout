package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.cheatwatch.Suspicion;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.Suspects;
import de.raindancer118.hypixelscout.ui.hud.SuspectsEditorScreen;
import de.raindancer118.hypixelscout.ui.widget.ActionButton;
import de.raindancer118.hypixelscout.ui.widget.Scissor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.List;

/**
 * The Cheats tab of {@link ScoutScreen}: everybody seen doing anything suspicious this round, the
 * surest first, on the left; the chosen one's every check on the right — with the verdict buttons
 * under it.
 *
 * <p>Ported from 26.2's {@code ui.screen.CheatsPage}: the list is 26.2's own {@code
 * ObjectSelectionList} there; 1.8.9 has nothing built for that, so this keeps a plain list of the
 * suspects it last drew, a scroll offset and a selected row index, hit-tested by hand against the
 * same rectangles {@link #draw} paints — the same pattern {@link ScoutScreen}'s own game list uses.
 */
final class CheatsPage {
	private static final int ROW = 24;
	private static final int HEAD = 16;

	private final HypixelScout mod;
	private final ScoutScreen screen;
	private final Minecraft minecraft;

	private int left;
	private int top;
	private int width;
	private int bottom;
	private int listWidth;
	private int scroll;
	private List<Suspicion.Suspect> suspects = new ArrayList<Suspicion.Suspect>();
	private String selected;
	private final List<GuiButton> verdictButtons = new ArrayList<GuiButton>();

	CheatsPage(HypixelScout mod, ScoutScreen screen) {
		this.mod = mod;
		this.screen = screen;
		this.minecraft = Minecraft.getMinecraft();
	}

	void init(int left, int top, int width, int bottom, java.util.function.IntSupplier nextId,
			java.util.function.Consumer<GuiButton> add) {
		this.left = left;
		this.top = top;
		this.width = width;
		this.bottom = bottom;
		this.listWidth = Math.max(150, width * 9 / 20);
		verdictButtons.clear();

		int right = left + width;
		for (final PartyReport.Channel channel : PartyReport.Channel.values()) {
			String key = channel == PartyReport.Channel.PARTY ? "message.hypixelscout.cheat.to_party"
					: "message.hypixelscout.cheat.to_team";
			GuiButton report = new ActionButton(nextId.getAsInt(),
					channel == PartyReport.Channel.PARTY ? right - 64 : right - 128, top - 2, 62, 16,
					StatCollector.translateToLocal(key), new Runnable() {
						@Override
						public void run() {
							mod.partyReport().sendCheats(channel, mod.cheats().suspicion());
						}
					});
			report.enabled = mod.partyReport().canSendPlayer(channel);
			add.accept(report);
		}

		add.accept(new ActionButton(nextId.getAsInt(), right - 200, top - 2, 70, 16,
				StatCollector.translateToLocal("message.hypixelscout.suspects.options"), new Runnable() {
					@Override
					public void run() {
						minecraft.displayGuiScreen(new CheatChecksScreen(mod, screen));
					}
				}));
		add.accept(new ActionButton(nextId.getAsInt(), right - 272, top - 2, 70, 16,
				StatCollector.translateToLocal("message.hypixelscout.suspects.move"), new Runnable() {
					@Override
					public void run() {
						minecraft.displayGuiScreen(new SuspectsEditorScreen(mod, screen));
					}
				}));

		int panelLeft = left + listWidth + 8;
		int panelWidth = right - panelLeft;
		int buttonY = bottom - 24;
		int buttonWidth = (panelWidth - 12) / 3;

		GuiButton wrong = new ActionButton(nextId.getAsInt(), panelLeft, buttonY, buttonWidth, 20,
				StatCollector.translateToLocal("message.hypixelscout.suspects.wrong"), new Runnable() {
					@Override
					public void run() {
						verdict(false);
					}
				});
		GuiButton right2 = new ActionButton(nextId.getAsInt(), panelLeft + buttonWidth + 6, buttonY, buttonWidth, 20,
				StatCollector.translateToLocal("message.hypixelscout.suspects.right"), new Runnable() {
					@Override
					public void run() {
						verdict(true);
					}
				});
		GuiButton profile = new ActionButton(nextId.getAsInt(), panelLeft + 2 * (buttonWidth + 6), buttonY,
				buttonWidth, 20, StatCollector.translateToLocal("message.hypixelscout.suspects.profile"),
				new Runnable() {
					@Override
					public void run() {
						if (selected != null) {
							minecraft.displayGuiScreen(new ProfileScreen(mod, selected,
									mod.roster().uuidOf(selected), screen));
						}
					}
				});
		for (GuiButton button : new GuiButton[] {wrong, right2, profile}) {
			add.accept(button);
			verdictButtons.add(button);
		}
		tick();
	}

	private void verdict(boolean cheating) {
		if (selected == null) {
			return;
		}
		mod.cheats().verdict(selected, null, cheating);
		if (!cheating) {
			selected = null;
		}
		tick();
	}

	void tick() {
		suspects = mod.cheats().suspects();
		if (selected == null || none(selected)) {
			selected = suspects.isEmpty() ? null : suspects.get(0).player();
		}
		for (GuiButton button : verdictButtons) {
			button.enabled = selected != null;
		}
	}

	private boolean none(String player) {
		for (Suspicion.Suspect suspect : suspects) {
			if (suspect.player().equals(player)) {
				return false;
			}
		}
		return true;
	}

	/** For the client game test: how many suspects the list shows. */
	int shownRows() {
		return suspects.size();
	}

	private int contentHeight() {
		return suspects.size() * ROW;
	}

	private int listTop() {
		return top + 18;
	}

	private int listBottom() {
		return bottom;
	}

	void onScroll(int direction) {
		int max = Math.max(0, contentHeight() - (listBottom() - listTop()));
		scroll = clamp(scroll - direction * 12, 0, max);
	}

	private static int clamp(int value, int min, int max) {
		return value < min ? min : value > max ? max : value;
	}

	boolean mouseClicked(int mouseX, int mouseY) {
		int listTop = listTop();
		if (mouseX < left || mouseX >= left + listWidth || mouseY < listTop || mouseY >= listBottom()) {
			return false;
		}

		int index = (mouseY - listTop + scroll) / ROW;
		if (index >= 0 && index < suspects.size()) {
			selected = suspects.get(index).player();
			tick();
			return true;
		}
		return false;
	}

	void draw(FontRenderer font, int mouseX, int mouseY) {
		long flagged = 0;
		for (Suspicion.Suspect suspect : suspects) {
			if (suspect.flagged()) {
				flagged++;
			}
		}

		String caption = !mod.settings().cheats.enabled ? "§c" + StatCollector.translateToLocal("message.hypixelscout.cheat.off")
				: "§7" + StatCollector.translateToLocalFormatted("message.hypixelscout.suspects.caption",
						suspects.size(), flagged);
		ScoutTheme.text(font, ScoutTheme.fit(font, caption, width - 280), left, top + 2, ScoutTheme.TEXT);

		if (suspects.isEmpty()) {
			int middle = (top + bottom) / 2;
			ScoutTheme.textCentred(font, StatCollector.translateToLocal("message.hypixelscout.suspects.none"),
					left + width / 2, middle - 6, ScoutTheme.TEXT);
			ScoutTheme.textCentred(font, StatCollector.translateToLocal("message.hypixelscout.suspects.none.hint"),
					left + width / 2, middle + 6, ScoutTheme.TEXT_DIM);
			return;
		}

		drawList(font, mouseX, mouseY);

		for (Suspicion.Suspect suspect : suspects) {
			if (suspect.player().equals(selected)) {
				drawDetail(font, suspect);
				break;
			}
		}
	}

	private void drawList(FontRenderer font, int mouseX, int mouseY) {
		int listTop = listTop();
		int listBottom = listBottom();
		Scissor.enable(left, listTop, listWidth, listBottom - listTop);

		int y = listTop - scroll;
		for (Suspicion.Suspect suspect : suspects) {
			if (y + ROW > listTop && y < listBottom) {
				drawSuspectRow(font, suspect, left, y, listWidth, mouseX, mouseY);
			}
			y += ROW;
		}

		Scissor.disable();
	}

	private void drawSuspectRow(FontRenderer font, Suspicion.Suspect suspect, int x, int y, int rowWidth,
			int mouseX, int mouseY) {
		boolean chosen = suspect.player().equals(selected);
		boolean hover = !chosen && mouseX >= x && mouseX < x + rowWidth && mouseY >= y && mouseY < y + ROW;
		if (hover || chosen) {
			ScoutTheme.rounded(x, y, rowWidth - 2, ROW - 1, chosen ? ScoutTheme.accent(0x40) : ScoutTheme.HOVER);
		}
		ScoutTheme.pill(x, y + 2, 3, ROW - 4, Teams.of(suspect.player()).argb());
		Heads.drawByName(suspect.player(), x + 6, y + (ROW - HEAD) / 2, HEAD);

		int nameX = x + 5 + HEAD + 6;
		String percent = Suspects.percent(suspect.confidence());
		int room = x + rowWidth - nameX - ScoutTheme.width(font, percent) - 8;
		String name = (suspect.flagged() ? Suspects.MARK : "§e? ") + Suspects.teamCode(suspect.player()) + suspect.player();
		ScoutTheme.text(font, ScoutTheme.fit(font, name, room), nameX, y + 2, ScoutTheme.TEXT);
		ScoutTheme.textRight(font, percent, x + rowWidth - 4, y + 2, ScoutTheme.TEXT);

		Suspicion.Seen top = suspect.checks().get(0);
		String status = (suspect.flagged() ? "§c" + StatCollector.translateToLocal("message.hypixelscout.suspects.flagged")
				: "§7" + StatCollector.translateToLocal("message.hypixelscout.suspects.suspected"))
				+ " §8· §7" + top.check().label() + " §8×" + top.count()
				+ (suspect.checks().size() > 1 ? " §8+" + (suspect.checks().size() - 1) : "");
		ScoutTheme.text(font, ScoutTheme.fit(font, status, x + rowWidth - nameX - 4), nameX, y + 12, ScoutTheme.TEXT_FAINT);
	}

	private void drawDetail(FontRenderer font, Suspicion.Suspect suspect) {
		int x = left + listWidth + 8;
		int y = top + 18;
		int panelWidth = left + width - x;
		int panelHeight = bottom - 28 - y;
		ScoutTheme.panel(x, y, panelWidth, panelHeight, 80);
		ScoutTheme.header(x, y, panelWidth, 80);

		Teams.Team team = Teams.of(suspect.player());
		String name = Suspects.teamCode(suspect.player()) + "§l" + suspect.player()
				+ (team == Teams.NONE ? "" : " §r§8· §7" + team.name());
		String sure = Suspects.percent(suspect.confidence()) + " §7" + StatCollector.translateToLocal("message.hypixelscout.suspects.sure");
		ScoutTheme.text(font, ScoutTheme.fit(font, name, panelWidth - ScoutTheme.width(font, sure) - 18), x + 6, y + 6, ScoutTheme.TEXT);
		ScoutTheme.textRight(font, sure, x + panelWidth - 6, y + 6, ScoutTheme.TEXT);

		int lineY = y + ScoutTheme.HEADER_HEIGHT + 5;
		long now = mod.cheats().tick();
		for (Suspicion.Seen seen : suspect.checks()) {
			if (lineY + 20 > y + panelHeight) {
				break;
			}
			String head = (seen.flagged() ? "§c⚑ " : "§8· ") + (seen.flagged() ? "§c" : "§f")
					+ seen.check().label() + " §8×" + seen.count();
			String percent = Suspects.percent(seen.confidence());
			ScoutTheme.text(font, head, x + 6, lineY, ScoutTheme.TEXT);
			ScoutTheme.textRight(font, percent, x + panelWidth - 6, lineY, ScoutTheme.TEXT);
			String evidence = "§7" + seen.detail() + " §8· " + StatCollector.translateToLocalFormatted(
					"message.hypixelscout.teams.bed.ago", ScoutScreen.ago(Math.max(0, now - seen.tick()) * 50));
			ScoutTheme.text(font, ScoutTheme.fit(font, evidence, panelWidth - 22), x + 16, lineY + 10, ScoutTheme.TEXT_DIM);
			lineY += 22;
		}
	}
}
