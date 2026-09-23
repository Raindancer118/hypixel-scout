package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ThreatTest {
	private static PlayerStats typical(int stars, double fkdr) {
		return TypicalPlayers.of(stars, fkdr);
	}

	private static PlayerStats.Builder base() {
		return PlayerStats.builder("Someone", UUID.randomUUID()).stars(100);
	}

	@Test
	void forATypicalPlayerTheCombatIndexIsStillStarsTimesTheSquareOfTheFkdr() {
		assertThat(Threat.combatIndex(typical(100, 3.0))).isCloseTo(900.0, within(15.0));
		assertThat(Threat.combatIndex(typical(400, 1.0))).isCloseTo(400.0, within(8.0));
	}

	@Test
	void forATypicalPlayerTheBedIndexMeetsTheCombatIndex() {
		PlayerStats player = typical(300, 4.0);
		assertThat(Threat.bedIndex(player)).isCloseTo(Threat.combatIndex(player), within(Threat.combatIndex(player) * 0.03));
	}

	@Test
	void aBetterKdrAndWlrRaiseTheCombatIndex() {
		PlayerStats plain = base().finals(300, 100).kills(200, 100).games(150, 100).build();
		PlayerStats fighter = base().finals(300, 100).kills(400, 100).games(150, 100).build();
		PlayerStats winner = base().finals(300, 100).kills(200, 100).games(400, 100).build();

		assertThat(Threat.combatIndex(fighter)).isGreaterThan(Threat.combatIndex(plain));
		assertThat(Threat.combatIndex(winner)).isGreaterThan(Threat.combatIndex(plain));
	}

	@Test
	void theFkdrStillWeighsMostInAFight() {
		PlayerStats finals = base().finals(600, 100).kills(200, 100).games(150, 100).build();
		PlayerStats kills = base().finals(300, 100).kills(400, 100).games(150, 100).build();

		assertThat(Threat.combatIndex(finals)).isGreaterThan(Threat.combatIndex(kills));
	}

	@Test
	void aRunningWinstreakMakesAPlayerMoreDangerousInBothWays() {
		PlayerStats cold = base().finals(300, 100).kills(200, 100).games(150, 100).beds(200, 100).winstreak(0).build();
		PlayerStats hot = base().finals(300, 100).kills(200, 100).games(150, 100).beds(200, 100).winstreak(50).build();
		PlayerStats hidden = base().finals(300, 100).kills(200, 100).games(150, 100).beds(200, 100).build();

		assertThat(Threat.combatIndex(hot)).isCloseTo(Threat.combatIndex(cold) * 1.5, within(1e-6));
		assertThat(Threat.bedIndex(hot)).isCloseTo(Threat.bedIndex(cold) * 1.5, within(1e-6));
		// A hidden streak is no streak of zero, but it is no reason to warn either.
		assertThat(Threat.combatIndex(hidden)).isEqualTo(Threat.combatIndex(cold));
	}

	@Test
	void theWinstreakBonusStopsAtDouble() {
		PlayerStats hundred = base().finals(300, 100).kills(200, 100).games(150, 100).winstreak(100).build();
		PlayerStats thousand = base().finals(300, 100).kills(200, 100).games(150, 100).winstreak(1000).build();

		assertThat(Threat.combatIndex(thousand)).isEqualTo(Threat.combatIndex(hundred));
	}

	@Test
	void theBedIndexFollowsBedsBrokenAgainstBedsLost() {
		PlayerStats defender = base().finals(300, 100).games(150, 100).beds(100, 100).build();
		PlayerStats rusher = base().finals(300, 100).games(150, 100).beds(500, 100).build();

		assertThat(Threat.bedIndex(rusher)).isGreaterThan(Threat.bedIndex(defender) * 4);
		// Their fights are the same; only the beds tell them apart.
		assertThat(Threat.combatIndex(rusher)).isEqualTo(Threat.combatIndex(defender));
	}

	@Test
	void manyBedsAGameMakeARusher() {
		PlayerStats seldom = base().games(150, 100).beds(50, 25).build();
		PlayerStats often = base().games(150, 100).beds(500, 250).build();

		assertThat(Threat.bedIndex(often)).isGreaterThan(Threat.bedIndex(seldom));
	}

	@Test
	void perGameFiguresCanNeitherDrownNorMakeAPlayer() {
		// Twenty beds a game is a broken stat, not a player twenty times as dangerous.
		PlayerStats normal = base().games(150, 100).beds(200, 100).build();
		PlayerStats absurd = base().games(150, 100).beds(20_000, 10_000).build();

		assertThat(Threat.bedIndex(absurd) / Threat.bedIndex(normal)).isCloseTo(Threat.MAX_PER_GAME_FACTOR, within(1e-9));
	}

	@Test
	void anEmptyProfileIsNoThreatInAnyWay() {
		PlayerStats fresh = PlayerStats.builder("Fresh", UUID.randomUUID()).build();

		assertThat(Threat.combatIndex(fresh)).isZero();
		assertThat(Threat.bedIndex(fresh)).isZero();
		assertThat(Threat.of(fresh)).isEqualTo(Threat.NONE);
	}

	@Test
	void unknownAndNickedPlayersHaveNoIndex() {
		assertThat(Threat.combatIndex(null)).isNegative();
		assertThat(Threat.bedIndex(null)).isNegative();
		assertThat(Threat.combatIndex(PlayerStats.nicked("Nick", UUID.randomUUID()))).isNegative();
		assertThat(Threat.index(PlayerStats.nicked("Nick", UUID.randomUUID()), ThreatFocus.BOTH)).isNegative();
	}

	@Test
	void theIndexForBothIsTheGreaterOfTheTwo() {
		PlayerStats rusher = base().finals(300, 100).kills(200, 100).games(150, 100).beds(900, 100).build();

		assertThat(Threat.index(rusher, ThreatFocus.BOTH)).isEqualTo(Threat.bedIndex(rusher));
		assertThat(Threat.index(rusher, ThreatFocus.COMBAT)).isEqualTo(Threat.combatIndex(rusher));
		assertThat(Threat.index(rusher, ThreatFocus.BEDS)).isEqualTo(Threat.bedIndex(rusher));
	}

	@Test
	void theLevelRisesWithTheIndex() {
		assertThat(Threat.of(typical(20, 0.5))).isEqualTo(Threat.NONE);
		assertThat(Threat.of(typical(200, 1.0))).isEqualTo(Threat.LOW);
		assertThat(Threat.of(typical(200, 2.0))).isEqualTo(Threat.MEDIUM);
		assertThat(Threat.of(typical(300, 4.0))).isEqualTo(Threat.HIGH);
		assertThat(Threat.of(typical(400, 5.5))).isEqualTo(Threat.VERY_HIGH);
		assertThat(Threat.of(typical(800, 10.0))).isEqualTo(Threat.EXTREME);
		assertThat(Threat.of(typical(1500, 14.0))).isEqualTo(Threat.INSANE);
	}

	@Test
	void theOldBandEdgesStillHold() {
		// LOW ends at 500, MED at 3 000 and EXTREME starts at 30 000, as before there were seven levels.
		assertThat(Threat.ofIndex(499)).isEqualTo(Threat.LOW);
		assertThat(Threat.ofIndex(500)).isEqualTo(Threat.MEDIUM);
		assertThat(Threat.ofIndex(2_999)).isEqualTo(Threat.MEDIUM);
		assertThat(Threat.ofIndex(29_999)).isEqualTo(Threat.VERY_HIGH);
		assertThat(Threat.ofIndex(30_000)).isEqualTo(Threat.EXTREME);
	}

	@Test
	void theRatedLevelsAreInOrderAndLeaveOutTheUnknownOnes() {
		assertThat(Threat.rated()).containsExactly(Threat.NONE, Threat.LOW, Threat.MEDIUM, Threat.HIGH,
				Threat.VERY_HIGH, Threat.EXTREME, Threat.INSANE);
		assertThat(Threat.UNKNOWN.isRated()).isFalse();
		assertThat(Threat.NICKED.isRated()).isFalse();
	}

	@Test
	void everyLabelIsShortAsciiForTheTableAndChat() {
		for (Threat threat : Threat.values()) {
			assertThat(threat.label()).hasSizeLessThanOrEqualTo(7);
			assertThat(threat.label().chars()).allMatch(c -> c >= 32 && c < 127);
		}
	}

	@Test
	void aNickIsItsOwnKindOfUnknown() {
		assertThat(Threat.of(PlayerStats.nicked("Nick", UUID.randomUUID()))).isEqualTo(Threat.NICKED);
		assertThat(Threat.of(null)).isEqualTo(Threat.UNKNOWN);
	}
}
