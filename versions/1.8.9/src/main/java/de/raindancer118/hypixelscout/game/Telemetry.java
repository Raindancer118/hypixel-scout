package de.raindancer118.hypixelscout.game;

import de.raindancer118.cheatwatch.Check;
import de.raindancer118.cheatwatch.Suspicion;
import de.raindancer118.cheatwatch.Violation;
import de.raindancer118.cheatwatch.telemetry.TelemetryClient;
import de.raindancer118.cheatwatch.telemetry.TelemetryConfig;
import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.ui.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Scout's side of CheatWatch telemetry: the client, switched by {@code telemetry.enabled}, the notice
 * the first time a world is joined, and "what is sent" written where the player can open it.
 *
 * <p>Opt-out, as decided: on by default and said so on first launch, one click turns it off. Nothing
 * leaves the machine while {@code telemetry.endpoint} is empty — the shipped default until a receiving
 * server exists and the data-protection review of the opt-out model is done.
 */
public final class Telemetry {
	private final Supplier<ScoutSettings> settings;
	private final TelemetryClient client;
	private final Path previewFile;

	public Telemetry(Supplier<ScoutSettings> settings) {
		this.settings = settings;
		String endpoint = settings.get().telemetry.endpoint;
		TelemetryClient made;
		try {
			made = TelemetryClient.forEndpoint(TelemetryConfig.of(endpoint, "hypixel-scout", HypixelScout.VERSION, "1.8.9"));
		} catch (IllegalArgumentException refused) {
			// Not https: nothing is sent rather than something sent in the clear.
			made = TelemetryClient.forEndpoint(TelemetryConfig.of("", "hypixel-scout", HypixelScout.VERSION, "1.8.9"));
		}
		client = made;
		client.setEnabled(settings.get().telemetry.enabled);
		client.start();
		previewFile = new File(Minecraft.getMinecraft().mcDataDir,
				"logs" + File.separator + "hypixelscout" + File.separator + "telemetry-preview.json").toPath();
	}

	public TelemetryClient client() {
		return client;
	}

	/** Every tick: follows the switch, and shows the notice once a player is in a world. */
	public void tick(Minecraft minecraft, Runnable save) {
		boolean on = settings.get().telemetry.enabled;
		if (client.enabled() != on) {
			client.setEnabled(on);
		}
		if (minecraft.thePlayer != null && !settings.get().telemetry.noticeShown) {
			settings.get().telemetry.noticeShown = true;
			save.run();
			Chat.say(notice());
		}
	}

	/** What the notice says, with [Turn off] and [What is sent] one click away. */
	public IChatComponent notice() {
		boolean sending = !settings.get().telemetry.endpoint.isEmpty();
		IChatComponent text = new ChatComponentTranslation(sending ? "message.hypixelscout.telemetry.notice"
				: "message.hypixelscout.telemetry.notice_idle");
		text.appendSibling(new ChatComponentText(" "))
				.appendSibling(link("message.hypixelscout.telemetry.turn_off", "/scout telemetry off"))
				.appendSibling(new ChatComponentText(" "))
				.appendSibling(link("message.hypixelscout.telemetry.what", "/scout telemetry show"));
		return text;
	}

	public void sighting(Violation violation) {
		client.sighting(violation);
	}

	public void flag(Suspicion.Flag flag) {
		client.flag(flag);
	}

	public void verdict(String player, Check check, boolean cheating) {
		client.verdict(player, check, cheating);
	}

	public void roundStarted(String mode) {
		client.roundStarted(mode);
	}

	public void roundEnded(long ticks, int players) {
		client.roundEnded(ticks, players);
	}

	/**
	 * Writes exactly what would be sent next (or what was sent last) to {@code telemetry-preview.json}
	 * in the log folder.
	 *
	 * @return the file, or {@code null} if it could not be written
	 */
	public Path writePreview() {
		String preview = client.preview();
		try {
			Files.createDirectories(previewFile.getParent());
			Files.write(previewFile, (preview.isEmpty() ? "{}\n" : preview + "\n").getBytes(StandardCharsets.UTF_8));
			return previewFile;
		} catch (IOException e) {
			return null;
		}
	}

	private static IChatComponent link(String key, String command) {
		IChatComponent component = new ChatComponentTranslation(key);
		ChatStyle style = new ChatStyle();
		style.setColor(EnumChatFormatting.AQUA);
		style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
		style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentTranslation(key + ".hover")));
		component.setChatStyle(style);
		return component;
	}
}
