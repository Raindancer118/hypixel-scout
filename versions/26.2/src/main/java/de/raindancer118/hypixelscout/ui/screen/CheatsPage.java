package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.cheat.Suspicion;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.Suspects;
import de.raindancer118.hypixelscout.ui.hud.SuspectsEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The Cheats tab of the scout screen: everybody seen doing anything suspicious this round, the surest
 * first, on the left; the chosen one's every check on the right — how often, how sure, flagged or
 * not, the latest evidence and how long ago — with the verdict buttons under it.
 *
 * <p>Rebuilt from the sensor every tick, like the other tabs, so a sighting shows while it is open.
 */
final class CheatsPage {
	private static final int ROW = 24;
	private static final int HEAD = 16;

	private final HypixelScout mod;
	private final ScoutScreen screen;
	private final Minecraft minecraft;
	private SuspectList list;
	private String selected;
	private int left;
	private int top;
	private int width;
	private int bottom;
	private final List<AbstractWidget> verdictButtons = new ArrayList<>();

	CheatsPage(HypixelScout mod, ScoutScreen screen) {
		this.mod = mod;
		this.screen = screen;
		this.minecraft = Minecraft.getInstance();
	}

	void init(int left, int top, int width, int bottom, Consumer<AbstractWidget> add) {
		this.left = left;
		this.top = top;
		this.width = width;
		this.bottom = bottom;
		verdictButtons.clear();

		int right = left + width;
		for (PartyReport.Channel channel : PartyReport.Channel.values()) {
			String key = channel == PartyReport.Channel.PARTY ? "message.hypixelscout.cheat.to_party" : "message.hypixelscout.cheat.to_team";
			Button report = Button.builder(Component.translatable(key),
							button -> mod.partyReport().sendCheats(channel, mod.cheats().suspicion()))
					.tooltip(Tooltip.create(Component.translatable(key + ".hover")))
					.bounds(channel == PartyReport.Channel.PARTY ? right - 64 : right - 128, top - 2, 62, 16).build();
			report.active = mod.partyReport().canSendPlayer(channel);
			add.accept(report);
		}
		add.accept(Button.builder(Component.translatable("message.hypixelscout.suspects.options"),
						button -> minecraft.gui.setScreen(new CheatChecksScreen(mod, screen)))
				.bounds(right - 200, top - 2, 70, 16).build());
		add.accept(Button.builder(Component.translatable("message.hypixelscout.suspects.move"),
						button -> minecraft.gui.setScreen(new SuspectsEditorScreen(mod::settings, mod.cheats(), mod::saveSettings, screen)))
				.bounds(right - 272, top - 2, 70, 16).build());

		int listWidth = listWidth();
		list = new SuspectList(listWidth, bottom - (top + 18) - 2, top + 18);
		list.setX(left);
		add.accept(list);

		int panelLeft = left + listWidth + 8;
		int panelWidth = right - panelLeft;
		int buttonY = bottom - 24;
		int buttonWidth = (panelWidth - 12) / 3;
		Button wrong = Button.builder(Component.translatable("message.hypixelscout.suspects.wrong"),
						button -> verdict(false))
				.tooltip(Tooltip.create(Component.translatable("message.hypixelscout.suspects.wrong.tooltip")))
				.bounds(panelLeft, buttonY, buttonWidth, 20).build();
		Button right2 = Button.builder(Component.translatable("message.hypixelscout.suspects.right"),
						button -> verdict(true))
				.tooltip(Tooltip.create(Component.translatable("message.hypixelscout.suspects.right.tooltip")))
				.bounds(panelLeft + buttonWidth + 6, buttonY, buttonWidth, 20).build();
		Button profile = Button.builder(Component.translatable("message.hypixelscout.suspects.profile"),
						button -> {
							if (selected != null) {
								minecraft.gui.setScreen(mod.profileScreen(selected, mod.roster().uuidOf(selected), screen));
							}
						})
				.bounds(panelLeft + 2 * (buttonWidth + 6), buttonY, buttonWidth, 20).build();
		for (Button button : List.of(wrong, right2, profile)) {
			add.accept(button);
			verdictButtons.add(button);
		}
		tick();
	}

	private int listWidth() {
		return Math.max(150, width * 9 / 20);
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
		if (list == null) {
			return;
		}
		List<Suspicion.Suspect> suspects = mod.cheats().suspects();
		list.sync(suspects);
		if (selected == null || suspects.stream().noneMatch(s -> s.player().equals(selected))) {
			selected = suspects.isEmpty() ? null : suspects.getFirst().player();
		}
		list.select(selected);
		for (AbstractWidget button : verdictButtons) {
			button.active = selected != null;
		}
	}

	/** For the client game test: how many suspects the list shows. */
	int shownRows() {
		return list == null ? 0 : list.children().size();
	}

	void draw(GuiGraphicsExtractor g) {
		List<Suspicion.Suspect> suspects = mod.cheats().suspects();
		long flagged = suspects.stream().filter(Suspicion.Suspect::flagged).count();
		String caption = !mod.settings().cheats.enabled ? "§c" + I18n.get("message.hypixelscout.cheat.off")
				: "§7" + I18n.get("message.hypixelscout.suspects.caption", suspects.size(), flagged);
		ScoutTheme.text(g, ScoutTheme.fit(caption, width - 280), left, top + 2, ScoutTheme.TEXT);

		if (suspects.isEmpty()) {
			int middle = (top + bottom) / 2;
			ScoutTheme.textCentred(g, I18n.get("message.hypixelscout.suspects.none"), left + width / 2, middle - 6, ScoutTheme.TEXT);
			ScoutTheme.textCentred(g, I18n.get("message.hypixelscout.suspects.none.hint"), left + width / 2, middle + 6,
					ScoutTheme.TEXT_DIM);
			return;
		}

		Suspicion.Suspect suspect = suspects.stream().filter(s -> s.player().equals(selected)).findFirst().orElse(null);
		if (suspect != null) {
			drawDetail(g, suspect);
		}
	}

	private void drawDetail(GuiGraphicsExtractor g, Suspicion.Suspect suspect) {
		int x = left + listWidth() + 8;
		int y = top + 18;
		int panelWidth = left + width - x;
		int panelHeight = bottom - 28 - y;
		ScoutTheme.panel(g, x, y, panelWidth, panelHeight, 80);
		ScoutTheme.header(g, x, y, panelWidth, 80);

		Teams.Team team = Teams.of(suspect.player());
		String name = Suspects.teamCode(suspect.player()) + "§l" + suspect.player()
				+ (team == Teams.NONE ? "" : " §r§8· §7" + team.name());
		String sure = Suspects.percent(suspect.confidence()) + " §7" + I18n.get("message.hypixelscout.suspects.sure");
		ScoutTheme.text(g, ScoutTheme.fit(name, panelWidth - ScoutTheme.width(sure) - 18), x + 6, y + 6, ScoutTheme.TEXT);
		ScoutTheme.textRight(g, sure, x + panelWidth - 6, y + 6, ScoutTheme.TEXT);

		int lineY = y + ScoutTheme.HEADER_HEIGHT + 5;
		long now = mod.cheats().tick();
		for (Suspicion.Seen seen : suspect.checks()) {
			if (lineY + 20 > y + panelHeight) {
				break;
			}
			String head = (seen.flagged() ? "§c⚑ " : "§8· ") + (seen.flagged() ? "§c" : "§f") + seen.check().label()
					+ " §8×" + seen.count();
			String percent = Suspects.percent(seen.confidence());
			ScoutTheme.text(g, head, x + 6, lineY, ScoutTheme.TEXT);
			ScoutTheme.textRight(g, percent, x + panelWidth - 6, lineY, ScoutTheme.TEXT);
			String evidence = "§7" + seen.detail() + " §8· " + I18n.get("message.hypixelscout.teams.bed.ago",
					ScoutScreen.ago(Math.max(0, now - seen.tick()) * 50));
			ScoutTheme.text(g, ScoutTheme.fit(evidence, panelWidth - 22), x + 16, lineY + 10, ScoutTheme.TEXT_DIM);
			lineY += 22;
		}
	}

	/** The suspects, one row each: face, name in the team's colour, confidence, and what was seen. */
	final class SuspectList extends ObjectSelectionList<SuspectList.Entry> {
		SuspectList(int width, int height, int y) {
			super(CheatsPage.this.minecraft, width, height, y, ROW);
		}

		@Override
		public int getRowWidth() {
			return getWidth() - 12;
		}

		void sync(List<Suspicion.Suspect> suspects) {
			List<Entry> current = children();
			boolean same = current.size() == suspects.size();
			for (int i = 0; same && i < suspects.size(); i++) {
				same = current.get(i).suspect.player().equals(suspects.get(i).player());
			}
			if (same) {
				for (int i = 0; i < suspects.size(); i++) {
					current.get(i).suspect = suspects.get(i);
				}
				return;
			}
			List<Entry> entries = new ArrayList<>();
			suspects.forEach(suspect -> entries.add(new Entry(suspect)));
			replaceEntries(entries);
		}

		void select(String player) {
			setSelected(children().stream().filter(entry -> Objects.equals(entry.suspect.player(), player))
					.findFirst().orElse(null));
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			private Suspicion.Suspect suspect;

			Entry(Suspicion.Suspect suspect) {
				this.suspect = suspect;
			}

			@Override
			public Component getNarration() {
				return Component.literal(suspect.player());
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
				int x = getContentX();
				int y = getContentY();
				int right = getContentRight();
				boolean chosen = suspect.player().equals(selected);
				if (hovered || chosen) {
					ScoutTheme.rounded(g, x - 2, y - 1, right - x + 4, getContentHeight() + 2,
							chosen ? ScoutTheme.accent(0x40) : ScoutTheme.HOVER);
				}
				ScoutTheme.pill(g, x, y + 1, 3, getContentHeight() - 2, Teams.of(suspect.player()).argb());
				Heads.drawByName(g, suspect.player(), x + 6, y + (getContentHeight() - HEAD) / 2, HEAD);

				int nameX = x + 5 + HEAD + 6;
				String percent = Suspects.percent(suspect.confidence());
				int room = right - nameX - ScoutTheme.width(percent) - 8;
				String name = (suspect.flagged() ? Suspects.MARK : "§e? ") + Suspects.teamCode(suspect.player()) + suspect.player();
				ScoutTheme.text(g, ScoutTheme.fit(name, room), nameX, y + 2, ScoutTheme.TEXT);
				ScoutTheme.textRight(g, percent, right - 2, y + 2, ScoutTheme.TEXT);
				Suspicion.Seen top = suspect.checks().getFirst();
				String status = (suspect.flagged() ? "§c" + I18n.get("message.hypixelscout.suspects.flagged")
						: "§7" + I18n.get("message.hypixelscout.suspects.suspected"))
						+ " §8· §7" + top.check().label() + " §8×" + top.count()
						+ (suspect.checks().size() > 1 ? " §8+" + (suspect.checks().size() - 1) : "");
				ScoutTheme.text(g, ScoutTheme.fit(status, right - nameX - 2), nameX, y + 12, ScoutTheme.TEXT_FAINT);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				selected = suspect.player();
				select(selected);
				tick();
				return true;
			}
		}
	}
}
