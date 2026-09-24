package de.raindancer118.hypixelscout.config;

import de.raindancer118.hypixelscout.core.HudMode;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.Threat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ScoutSettingsTest {
	@TempDir
	Path dir;

	@Test
	void aMissingFileGivesTheDefaultsAndWritesThemOut() {
		Path file = dir.resolve("hypixelscout.json");

		ScoutSettings settings = ScoutSettings.load(file);

		assertThat(settings.apiKey).isEmpty();
		assertThat(settings.table.mode).isEqualTo(HudMode.TOGGLE);
		assertThat(settings.table.sort).isEqualTo(SortMode.STARS);
		assertThat(settings.tooltip.enabled).isTrue();
		// Both replace something vanilla draws, so neither is on until asked for.
		assertThat(settings.tab.enabled).isFalse();
		assertThat(settings.nametag.stars).isFalse();
		assertThat(settings.queue.slots).hasSize(ScoutSettings.QUEUE_SLOTS);
		assertThat(settings.queue.slots[0]).isEqualTo("bedwars_eight_one");
		// The per-game rates are what tells a rusher from a camper, so they are on from the start.
		assertThat(settings.table.showBedsPerGame).isTrue();
		assertThat(settings.table.showKillsPerGame).isTrue();
		// Nothing is looked up in the waiting lobby unless asked for; threat is measured against
		// the player and their team.
		assertThat(settings.lookUpInLobby).isFalse();
		assertThat(settings.threatBasis).isEqualTo(de.raindancer118.hypixelscout.core.ThreatScale.Basis.TEAM);
		// The bands as they are, and the reports leave out whoever is below an even match.
		assertThat(settings.threatSensitivity).isEqualTo(100);
		assertThat(settings.threatReportFrom).isEqualTo(Threat.MEDIUM);
		// Half a second between chat lines, as asked for.
		assertThat(settings.reportIntervalTicks).isEqualTo(10);
		// The proximity popup is on, for enemies within twelve blocks, for four seconds.
		assertThat(settings.proximity.enabled).isTrue();
		assertThat(settings.proximity.radius).isEqualTo(12);
		assertThat(settings.proximity.seconds).isEqualTo(4);
		assertThat(settings.proximity.from).isEqualTo(Threat.NONE);
		// Joining the next game by itself is something to switch on knowingly.
		assertThat(settings.requeue.mode).isEqualTo(de.raindancer118.hypixelscout.core.RequeueMode.OFF);
		assertThat(settings.requeue.delaySeconds).isEqualTo(3);
		// Threat is about fights and beds alike until the player narrows it down.
		assertThat(settings.threatFocus).isEqualTo(de.raindancer118.hypixelscout.core.ThreatFocus.BOTH);
		// Flight paths, the alarm and the fireball aim are all on, but only in a game.
		assertThat(settings.projectiles.paths).isTrue();
		assertThat(settings.projectiles.alarm).isTrue();
		assertThat(settings.projectiles.sound).isTrue();
		assertThat(settings.projectiles.lock).isTrue();
		assertThat(settings.projectiles.aim).isTrue();
		assertThat(settings.projectiles.arrows).isTrue();
		assertThat(settings.projectiles.fireballs).isTrue();
		assertThat(settings.projectiles.onlyInGame).isTrue();
		// Pearls, the bow line and the blast preview join them.
		assertThat(settings.projectiles.pearls).isTrue();
		assertThat(settings.projectiles.pearlAim).isTrue();
		assertThat(settings.projectiles.bowAim).isTrue();
		assertThat(settings.projectiles.blastPreview).isTrue();
		// TNT, the void, beds and the edge markers are all on.
		assertThat(settings.awareness.tnt).isTrue();
		assertThat(settings.awareness.voidWarning).isTrue();
		assertThat(settings.awareness.bedDefense).isTrue();
		assertThat(settings.awareness.offscreen).isTrue();
		assertThat(settings.awareness.offscreenRange).isEqualTo(32);
		// Cheat detection watches, marks and says so, at the designed sensitivity.
		assertThat(settings.cheats.enabled).isTrue();
		assertThat(settings.cheats.chatAlerts).isTrue();
		assertThat(settings.cheats.mark).isTrue();
		assertThat(settings.cheats.sensitivity).isEqualTo(100);
		assertThat(settings.cheats.off).isEmpty();
		assertThat(settings.cheats.isOn(de.raindancer118.hypixelscout.cheat.Check.REACH)).isTrue();
		// Six callouts, the first the classic, all into team chat.
		assertThat(settings.callouts.messages).hasSize(ScoutSettings.CALLOUTS);
		assertThat(settings.callouts.messages[0]).isEqualTo("{team} inc");
		assertThat(settings.callouts.toParty).isFalse();
		assertThat(file).exists();
	}

	@Test
	void whatWasSavedIsWhatIsLoaded() {
		Path file = dir.resolve("hypixelscout.json");
		ScoutSettings settings = ScoutSettings.load(file);

		settings.apiKey = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";
		settings.table.mode = HudMode.ALWAYS;
		settings.table.sort = SortMode.FKDR;
		settings.table.placement = new TablePlacement(TableAnchor.BOTTOM_RIGHT, -0.01, -0.02);
		settings.alerts.streakThreshold = 25;
		settings.queue.slots[8] = "bedwars_two_four";
		settings.accent = Accent.AQUA;
		settings.threatSensitivity = 150;
		settings.threatReportFrom = Threat.VERY_HIGH;
		settings.save();

		ScoutSettings loaded = ScoutSettings.load(file);

		assertThat(loaded.apiKey).isEqualTo("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0");
		assertThat(loaded.table.mode).isEqualTo(HudMode.ALWAYS);
		assertThat(loaded.table.sort).isEqualTo(SortMode.FKDR);
		assertThat(loaded.table.placement)
				.isEqualTo(new TablePlacement(TableAnchor.BOTTOM_RIGHT, -0.01, -0.02));
		assertThat(loaded.alerts.streakThreshold).isEqualTo(25);
		assertThat(loaded.queue.slots[8]).isEqualTo("bedwars_two_four");
		assertThat(loaded.accent).isEqualTo(Accent.AQUA);
		assertThat(loaded.threatSensitivity).isEqualTo(150);
		assertThat(loaded.threatReportFrom).isEqualTo(Threat.VERY_HIGH);
	}

	@Test
	void theThreatSettingsAreKeptSane() throws Exception {
		Path file = dir.resolve("hypixelscout.json");
		Files.writeString(file, """
				{ "threatSensitivity": 5000, "threatReportFrom": "NICKED", "reportIntervalTicks": 0 }
				""", StandardCharsets.UTF_8);

		ScoutSettings settings = ScoutSettings.load(file);

		assertThat(settings.threatSensitivity).isEqualTo(ScoutSettings.MAX_SENSITIVITY);
		assertThat(settings.reportIntervalTicks).isEqualTo(10);
		// Only a level a player can actually be rated at is something to report from.
		assertThat(settings.threatReportFrom).isEqualTo(Threat.MEDIUM);

		Files.writeString(file, """
				{ "threatSensitivity": 1, "threatReportFrom": null, "proximity": null }
				""", StandardCharsets.UTF_8);
		settings = ScoutSettings.load(file);

		assertThat(settings.threatSensitivity).isEqualTo(ScoutSettings.MIN_SENSITIVITY);
		assertThat(settings.proximity).isNotNull();

		Files.writeString(file, """
				{ "proximity": { "radius": 900, "seconds": 0, "from": "UNKNOWN" } }
				""", StandardCharsets.UTF_8);
		ScoutSettings proximity = ScoutSettings.load(file);
		assertThat(proximity.proximity.radius).isEqualTo(ScoutSettings.MAX_RADIUS);
		assertThat(proximity.proximity.seconds).isEqualTo(1);
		assertThat(proximity.proximity.from).isEqualTo(Threat.NONE);

		Files.writeString(file, """
				{ "awareness": { "offscreenRange": 5000 } }
				""", StandardCharsets.UTF_8);
		assertThat(ScoutSettings.load(file).awareness.offscreenRange).isEqualTo(ScoutSettings.MAX_OFFSCREEN_RANGE);
		Files.writeString(file, """
				{ "cheats": { "sensitivity": 9000 } }
				""", StandardCharsets.UTF_8);
		assertThat(ScoutSettings.load(file).cheats.sensitivity).isEqualTo(ScoutSettings.MAX_CHEAT_SENSITIVITY);
		Files.writeString(file, """
				{ "cheats": { "off": ["REACH", "NOT_A_CHECK", "REACH", "FLY"] } }
				""", StandardCharsets.UTF_8);
		ScoutSettings checks = ScoutSettings.load(file);
		assertThat(checks.cheats.off).containsExactly(de.raindancer118.hypixelscout.cheat.Check.REACH,
				de.raindancer118.hypixelscout.cheat.Check.FLY);
		assertThat(checks.cheats.isOn(de.raindancer118.hypixelscout.cheat.Check.REACH)).isFalse();
		assertThat(checks.cheats.isOn(de.raindancer118.hypixelscout.cheat.Check.SPEED)).isTrue();
		Files.writeString(file, """
				{ "cheats": null }
				""", StandardCharsets.UTF_8);
		assertThat(ScoutSettings.load(file).cheats).isNotNull();
		Files.writeString(file, """
				{ "awareness": null }
				""", StandardCharsets.UTF_8);
		assertThat(ScoutSettings.load(file).awareness).isNotNull();

		Files.writeString(file, """
				{ "requeue": { "mode": "SOMETIMES", "delaySeconds": 99 } }
				""", StandardCharsets.UTF_8);
		ScoutSettings requeue = ScoutSettings.load(file);
		assertThat(requeue.requeue.mode).isEqualTo(de.raindancer118.hypixelscout.core.RequeueMode.OFF);
		assertThat(requeue.requeue.delaySeconds).isEqualTo(ScoutSettings.MAX_REQUEUE_DELAY);
		assertThat(settings.threatReportFrom).isEqualTo(Threat.MEDIUM);
	}

	@Test
	void aBrokenFileFallsBackToDefaultsAndIsKeptForTheUserToRepair() throws Exception {
		Path file = dir.resolve("hypixelscout.json");
		Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);

		ScoutSettings settings = ScoutSettings.load(file);

		assertThat(settings.table.mode).isEqualTo(HudMode.TOGGLE);
		// Overwriting it would throw away whatever the user was halfway through typing.
		assertThat(Files.readString(file)).isEqualTo("{ this is not json");
	}

	@Test
	void valuesOutOfRangeAreClampedAndMissingSectionsFilledIn() throws Exception {
		Path file = dir.resolve("hypixelscout.json");
		Files.writeString(file, """
				{
				  "apiKey": null,
				  "table": { "mode": "NONSENSE", "maxRows": 900, "scale": 9.0, "opacity": -4 },
				  "tooltip": { "angle": 0.0 },
				  "alerts": null,
				  "threatFocus": "NONSENSE",
				  "projectiles": null,
				  "callouts": { "messages": ["{team} rush", null] },
				  "queue": { "slots": ["bedwars_four_four"] },
				  "cacheMinutes": 0
				}
				""", StandardCharsets.UTF_8);

		ScoutSettings settings = ScoutSettings.load(file);

		assertThat(settings.apiKey).isEmpty();
		assertThat(settings.table.mode).isEqualTo(HudMode.TOGGLE);
		assertThat(settings.table.maxRows).isEqualTo(ScoutSettings.MAX_ROWS);
		assertThat(settings.table.scale).isEqualTo(ScoutSettings.MAX_SCALE);
		assertThat(settings.table.opacity).isZero();
		assertThat(settings.tooltip.angle).isEqualTo(ScoutSettings.MIN_ANGLE);
		assertThat(settings.alerts).isNotNull();
		assertThat(settings.alerts.nickAlert).isTrue();
		assertThat(settings.queue.slots).hasSize(ScoutSettings.QUEUE_SLOTS);
		assertThat(settings.queue.slots[0]).isEqualTo("bedwars_four_four");
		assertThat(settings.queue.slots[1]).isEmpty();
		assertThat(settings.cacheMinutes).isEqualTo(1);
		assertThat(settings.threatFocus).isEqualTo(de.raindancer118.hypixelscout.core.ThreatFocus.BOTH);
		assertThat(settings.projectiles).isNotNull();
		assertThat(settings.projectiles.alarm).isTrue();
		// A short list is filled up with empty slots, not with the defaults over what was typed.
		assertThat(settings.callouts.messages).hasSize(ScoutSettings.CALLOUTS);
		assertThat(settings.callouts.messages[0]).isEqualTo("{team} rush");
		assertThat(settings.callouts.messages[1]).isEmpty();
		assertThat(settings.callouts.messages[5]).isEmpty();
		// A short list is filled up with empty slots, not with the defaults over what was typed.
		assertThat(settings.callouts.messages).hasSize(ScoutSettings.CALLOUTS);
		assertThat(settings.callouts.messages[0]).isEqualTo("{team} rush");
		assertThat(settings.callouts.messages[1]).isEmpty();
		assertThat(settings.callouts.messages[5]).isEmpty();
		assertThat(settings.threatBasis).isEqualTo(de.raindancer118.hypixelscout.core.ThreatScale.Basis.TEAM);
	}

	@Test
	void aSaveNeverLeavesATemporaryFileBehind() throws Exception {
		Path file = dir.resolve("hypixelscout.json");
		ScoutSettings.load(file).save();

		try (var files = Files.list(dir)) {
			assertThat(files.map(p -> p.getFileName().toString())).containsExactly("hypixelscout.json");
		}
	}

	@Test
	void theCosineIsWhatThePickerComparesAgainst() {
		ScoutSettings settings = new ScoutSettings();
		settings.tooltip.angle = 60.0;

		assertThat(settings.tooltip.cosine()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
	}
}
