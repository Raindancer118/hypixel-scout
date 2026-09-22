package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.HypixelApiException;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.ProfileMetrics;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.ProfileView;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The full profile of one player: everything the API gives, laid out to be read rather than
 * glanced at.
 *
 * <p>Works for anybody, not only the people in your game — a name without a UUID is resolved through
 * Mojang first. Both that and the stats fetch happen off the render thread; this screen only reads
 * what has arrived, and fills itself in when it does.
 */
public final class ProfileScreen extends Screen {
	private static final int MAX_WIDTH = 440;

	private final HypixelScout mod;
	private final StatsService stats;
	private final Screen parent;

	private volatile String name;
	private volatile UUID uuid;
	private volatile String error;
	private volatile boolean resolving;

	private EditBox search;

	public ProfileScreen(HypixelScout mod, String name, UUID uuid, Screen parent) {
		super(Component.translatable("message.hypixelscout.profile.title"));
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
	protected void init() {
		int left = left();
		int top = 10;

		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
				.bounds(left, top, 50, 20).build());

		search = new EditBox(font, left + 56, top, contentWidth() - 56 - 84, 20,
				Component.translatable("message.hypixelscout.lookup.hint"));
		search.setHint(Component.translatable("message.hypixelscout.lookup.hint"));
		search.setMaxLength(16);
		addRenderableWidget(search);

		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.lookup.go"),
						button -> lookUp(search.getValue()))
				.bounds(left + contentWidth() - 80, top, 80, 20).build());

		int bottom = height - 28;
		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.refresh"), button -> {
					if (uuid != null) {
						stats.forget(uuid);
						stats.request(uuid, name);
					}
				})
				.bounds(width / 2 - 154, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.profile.plancke"),
						button -> {
							if (name != null) {
								ConfirmLinkScreen.confirmLinkNow(this, "https://plancke.io/hypixel/player/stats/"
										+ name + "#BedWars");
							}
						})
				.bounds(width / 2 - 50, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> minecraft.gui.setScreen(null))
				.bounds(width / 2 + 54, bottom, 100, 20).build());
	}

	private void lookUp(String typed) {
		String wanted = typed == null ? "" : typed.trim();
		if (!wanted.matches("\\w{1,16}") || resolving) {
			return;
		}

		mod.lookups().add(wanted);
		mod.saveSettings();
		search.setValue("");

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

	/** Name to UUID through Mojang, on the worker: it is a network round trip. */
	private void resolve(String wanted) {
		resolving = true;
		mod.worker().execute(() -> {
			try {
				UUID resolved = mod.mojang().uuidOf(wanted);
				if (!wanted.equals(name)) {
					return;
				}

				if (resolved == null) {
					error = I18n.get("message.hypixelscout.profile.no_account", wanted);
					return;
				}

				uuid = resolved;
				stats.request(resolved, wanted);
			} catch (HypixelApiException e) {
				error = e.getMessage();
			} finally {
				resolving = false;
			}
		});
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (search != null && search.isFocused()
				&& (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			lookUp(search.getValue());
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);

		int left = left();
		int top = 40;
		int middle = height / 2 - 10;

		if (name == null) {
			ScoutTheme.textCentred(g, I18n.get("message.hypixelscout.profile.empty"), width / 2, middle,
					ScoutTheme.TEXT_DIM);
			return;
		}

		if (error != null) {
			ScoutTheme.textCentred(g, "§c" + error, width / 2, middle, ScoutTheme.TEXT);
			return;
		}

		PlayerStats profile = uuid == null ? null : stats.peek(uuid);
		if (profile == null) {
			String failure = uuid == null ? null : stats.failureFor(uuid);
			ScoutTheme.textCentred(g, failure != null && !stats.isPending(uuid) ? "§c" + failure
					: I18n.get("message.hypixelscout.profile.loading", name), width / 2, middle, ScoutTheme.TEXT);
			return;
		}

		if (profile.isNicked()) {
			ProfileView.header(g, name, uuid, profile, left, top, contentWidth(), 88);
			ScoutTheme.textCentred(g, I18n.get("message.hypixelscout.profile.nicked"), width / 2, top + 64,
					ScoutTheme.TEXT_DIM);
			return;
		}

		int bottom = height - 34;
		int y = ProfileView.header(g, name, uuid, profile, left, top, contentWidth(), 88) + ProfileView.GAP;
		y = ProfileView.cards(g, profile, left, y, contentWidth(), bottom, 80) + ProfileView.GAP;
		ProfileView.pace(g, profile, left, y, contentWidth(), bottom, 80);
	}

	/** For the client game test: the player this screen is about. */
	public UUID uuid() {
		return uuid;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
