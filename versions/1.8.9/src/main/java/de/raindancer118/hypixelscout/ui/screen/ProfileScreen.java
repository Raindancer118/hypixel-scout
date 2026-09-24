package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.HypixelApiException;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.ui.ProfileView;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.widget.ActionButton;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The full profile of one player: everything the API gives, laid out to be read rather than
 * glanced at.
 *
 * <p>Ported from 26.2's {@code ui.screen.ProfileScreen}. Works for anybody, not only the people in
 * the player's own game — a name without a UUID is resolved through Mojang first, off the render
 * thread ({@link HypixelScout#worker()}), the same as 26.2 does it on its own worker.
 */
public final class ProfileScreen extends GuiScreen {
	private static final int MAX_WIDTH = 440;

	private final HypixelScout mod;
	private final StatsService stats;
	private final GuiScreen parent;

	private volatile String name;
	private volatile UUID uuid;
	private volatile String error;
	private volatile boolean resolving;

	private net.minecraft.client.gui.GuiTextField searchBox;
	private final List<GuiButton> sendButtons = new ArrayList<GuiButton>();

	public ProfileScreen(HypixelScout mod, String name, UUID uuid, GuiScreen parent) {
		this.mod = mod;
		this.stats = mod.stats();
		this.parent = parent;
		this.name = name;
		this.uuid = uuid;

		if (name != null && uuid != null) {
			stats.request(uuid, name);
		} else if (name != null) {
			resolve(name);
		}
	}

	private int contentWidth() {
		return Math.min(width - 24, MAX_WIDTH);
	}

	private int left() {
		return (width - contentWidth()) / 2;
	}

	@Override
	public void initGui() {
		buttonList.clear();
		sendButtons.clear();
		int id = 0;
		int left = left();
		int top = 10;

		buttonList.add(new ActionButton(id++, left, top, 50, 20, StatCollector.translateToLocal("gui.back"),
				new Runnable() {
					@Override
					public void run() {
						onGuiClosed();
						mc.displayGuiScreen(parent);
					}
				}));

		searchBox = new net.minecraft.client.gui.GuiTextField(id++, fontRendererObj, left + 56, top,
				contentWidth() - 56 - 84, 20);
		searchBox.setMaxStringLength(16);

		buttonList.add(new ActionButton(id++, left + contentWidth() - 80, top, 80, 20,
				StatCollector.translateToLocal("message.hypixelscout.lookup.go"), new Runnable() {
					@Override
					public void run() {
						lookUp(searchBox.getText());
					}
				}));

		int bottom = height - 28;
		int buttonWidth = Math.min(100, (width - 20 - 4 * 4) / 5);
		int x = width / 2 - (5 * buttonWidth + 4 * 4) / 2;

		buttonList.add(new ActionButton(id++, x, bottom, buttonWidth, 20,
				StatCollector.translateToLocal("message.hypixelscout.refresh"), new Runnable() {
					@Override
					public void run() {
						if (uuid != null) {
							stats.forget(uuid);
							stats.request(uuid, name);
						}
					}
				}));
		x += buttonWidth + 4;

		buttonList.add(new ActionButton(id++, x, bottom, buttonWidth, 20,
				StatCollector.translateToLocal("message.hypixelscout.profile.plancke"), new Runnable() {
					@Override
					public void run() {
						if (name != null) {
							openPlancke();
						}
					}
				}));
		x += buttonWidth + 4;

		for (final PartyReport.Channel channel : PartyReport.Channel.values()) {
			String key = "message.hypixelscout.profile.send." + channel.name().toLowerCase(java.util.Locale.ROOT);
			GuiButton send = new ActionButton(id++, x, bottom, buttonWidth, 20, StatCollector.translateToLocal(key),
					new Runnable() {
						@Override
						public void run() {
							PlayerStats profile = uuid == null ? null : stats.peek(uuid);
							mod.partyReport().sendPlayer(channel, name, profile);
						}
					});
			send.enabled = mod.partyReport().canSendPlayer(channel);
			sendButtons.add(send);
			buttonList.add(send);
			x += buttonWidth + 4;
		}

		buttonList.add(new ActionButton(id++, x, bottom, buttonWidth, 20, StatCollector.translateToLocal("gui.done"),
				new Runnable() {
					@Override
					public void run() {
						onGuiClosed();
						mc.displayGuiScreen(null);
					}
				}));
	}

	/** The system browser is not something a headless test can open; kept separate to stay testable. */
	private void openPlancke() {
		try {
			java.awt.Desktop.getDesktop().browse(new java.net.URI("https://plancke.io/hypixel/player/stats/" + name + "#BedWars"));
		} catch (Exception e) {
			// No desktop/browser available (a server, a headless test) — nothing sensible to do about it.
		}
	}

	@Override
	public void updateScreen() {
		boolean known = name != null && uuid != null && stats.peek(uuid) != null;
		for (int i = 0; i < sendButtons.size(); i++) {
			sendButtons.get(i).enabled = known && mod.partyReport().canSendPlayer(PartyReport.Channel.values()[i]);
		}
	}

	private void lookUp(String typed) {
		String wanted = typed == null ? "" : typed.trim();
		if (!wanted.matches("\\w{1,16}") || resolving) {
			return;
		}

		mod.lookups().add(wanted);
		mod.saveSettings();
		searchBox.setText("");

		UUID inGame = mod.roster().uuidOf(wanted);
		name = wanted;
		error = null;
		uuid = inGame;

		if (inGame != null) {
			stats.request(inGame, wanted);
		} else {
			resolve(wanted);
		}
	}

	private void resolve(final String wanted) {
		resolving = true;
		mod.worker().execute(new Runnable() {
			@Override
			public void run() {
				try {
					final UUID resolved = mod.mojang().uuidOf(wanted);
					if (!wanted.equals(name)) {
						return;
					}

					if (resolved == null) {
						error = StatCollector.translateToLocalFormatted("message.hypixelscout.profile.no_account", wanted);
						return;
					}

					uuid = resolved;
					stats.request(resolved, wanted);
				} catch (HypixelApiException e) {
					error = e.getMessage();
				} finally {
					resolving = false;
				}
			}
		});
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (searchBox != null && searchBox.isFocused()) {
			if (keyCode == org.lwjgl.input.Keyboard.KEY_RETURN || keyCode == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) {
				lookUp(searchBox.getText());
				return;
			}
			if (searchBox.textboxKeyTyped(typedChar, keyCode)) {
				return;
			}
		}
		super.keyTyped(typedChar, keyCode);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		if (searchBox != null) {
			searchBox.mouseClicked(mouseX, mouseY, mouseButton);
		}
		super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button instanceof de.raindancer118.hypixelscout.ui.widget.Clickable) {
			((de.raindancer118.hypixelscout.ui.widget.Clickable) button).onClick();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();

		int left = left();
		int top = 40;
		int middle = height / 2 - 10;
		FontRenderer font = fontRendererObj;

		if (name == null) {
			ScoutTheme.textCentred(font, StatCollector.translateToLocal("message.hypixelscout.profile.empty"),
					width / 2, middle, ScoutTheme.TEXT_DIM);
		} else if (error != null) {
			ScoutTheme.textCentred(font, "§c" + error, width / 2, middle, ScoutTheme.TEXT);
		} else {
			PlayerStats profile = uuid == null ? null : stats.peek(uuid);
			if (profile == null) {
				String failure = uuid == null ? null : stats.failureFor(uuid);
				ScoutTheme.textCentred(font, failure != null && !stats.isPending(uuid) ? "§c" + failure
						: StatCollector.translateToLocalFormatted("message.hypixelscout.profile.loading", name),
						width / 2, middle, ScoutTheme.TEXT);
			} else if (profile.isNicked()) {
				ProfileView.header(font, name, uuid, profile, left, top, contentWidth(), 88);
				ScoutTheme.textCentred(font, StatCollector.translateToLocal("message.hypixelscout.profile.nicked"),
						width / 2, top + 64, ScoutTheme.TEXT_DIM);
			} else {
				int bottom = height - 34;
				int y = ProfileView.header(font, name, uuid, profile, left, top, contentWidth(), 88) + ProfileView.GAP;
				y = ProfileView.cards(font, profile, left, y, contentWidth(), bottom, 80) + ProfileView.GAP;
				ProfileView.pace(font, profile, left, y, contentWidth(), bottom, 80);
			}
		}

		if (searchBox != null) {
			searchBox.drawTextBox();
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	/** For the client game test: the player this screen is about. */
	public UUID uuid() {
		return uuid;
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void onGuiClosed() {
		mod.saveSettings();
	}
}
