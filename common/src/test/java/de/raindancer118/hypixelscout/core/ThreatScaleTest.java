package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ThreatScaleTest {
	/** A typical player with FKDR 1: both their indices are the star count. */
	private static PlayerStats index(int stars) {
		return TypicalPlayers.index(stars);
	}

	/** Fights like {@code stars}, but breaks beds like somebody with {@code bedFactor} times the index. */
	private static PlayerStats rusher(int stars, double bblrFactor) {
		PlayerStats typical = TypicalPlayers.index(stars);
		return PlayerStats.builder("Rusher" + stars, UUID.randomUUID()).stars(stars)
				.finals(typical.getFinalKills(), typical.getFinalDeaths())
				.kills(typical.getKills(), typical.getDeaths())
				.games(typical.getWins(), typical.getLosses())
				.beds(typical.getBedsBroken(), (int) Math.round(typical.getBedsLost() / bblrFactor))
				.build();
	}

	@Test
	void measuredAgainstMeAnEqualIsMediumAndFourTimesMeIsExtreme() {
		ThreatScale scale = ThreatScale.of(ThreatScale.Basis.ME, index(1000), List.of());

		assertThat(scale.threatOf(index(100))).isEqualTo(Threat.NONE);
		assertThat(scale.threatOf(index(400))).isEqualTo(Threat.LOW);
		assertThat(scale.threatOf(index(1000))).isEqualTo(Threat.MEDIUM);
		assertThat(scale.threatOf(index(2000))).isEqualTo(Threat.HIGH);
		assertThat(scale.threatOf(index(3000))).isEqualTo(Threat.VERY_HIGH);
		assertThat(scale.threatOf(index(4100))).isEqualTo(Threat.EXTREME);
		assertThat(scale.threatOf(index(10_000))).isEqualTo(Threat.INSANE);
	}

	@Test
	void aHigherSensitivityRatesTheSameEnemyAsMoreDangerous() {
		ThreatScale scale = ThreatScale.of(ThreatScale.Basis.ME, index(1000), List.of());
		PlayerStats enemy = index(1000);

		assertThat(scale.withSensitivity(1.0).threatOf(enemy)).isEqualTo(Threat.MEDIUM);
		assertThat(scale.withSensitivity(2.0).threatOf(enemy)).isEqualTo(Threat.HIGH);
		assertThat(scale.withSensitivity(0.4).threatOf(enemy)).isEqualTo(Threat.LOW);
	}

	@Test
	void theSensitivityAppliesToTheFixedBandsToo() {
		PlayerStats enemy = index(400);

		assertThat(ThreatScale.ABSOLUTE.threatOf(enemy)).isEqualTo(Threat.LOW);
		assertThat(ThreatScale.ABSOLUTE.withSensitivity(1.5).threatOf(enemy)).isEqualTo(Threat.MEDIUM);
		assertThat(ThreatScale.ABSOLUTE.withSensitivity(1.5).isRelative()).isFalse();
	}

	@Test
	void theSensitivityCannotRateUnknownOrNickedPlayers() {
		ThreatScale scale = ThreatScale.ABSOLUTE.withSensitivity(4.0);

		assertThat(scale.threatOf(null)).isEqualTo(Threat.UNKNOWN);
		assertThat(scale.threatOf(PlayerStats.nicked("Nick", UUID.randomUUID()))).isEqualTo(Threat.NICKED);
	}

	@Test
	void aSensitivityThatIsNoNumberIsTakenAsTheDefault() {
		assertThat(ThreatScale.ABSOLUTE.withSensitivity(Double.NaN).sensitivity()).isEqualTo(1.0);
		assertThat(ThreatScale.ABSOLUTE.withSensitivity(0).sensitivity()).isEqualTo(1.0);
	}

	@Test
	void theSamePlayerIsLessOfAThreatToAStrongerPlayer() {
		PlayerStats enemy = index(3000);

		assertThat(ThreatScale.of(ThreatScale.Basis.ME, index(500), List.of()).threatOf(enemy))
				.isEqualTo(Threat.EXTREME);
		assertThat(ThreatScale.of(ThreatScale.Basis.ME, index(5000), List.of()).threatOf(enemy))
				.isEqualTo(Threat.MEDIUM);
	}

	@Test
	void aStrongTeamRaisesTheBar() {
		// Me 400, teammates averaging 1600: the geometric mean is 800.
		ThreatScale scale = ThreatScale.of(ThreatScale.Basis.TEAM, index(400), List.of(index(1000), index(2200)));

		assertThat(scale.combatReference()).isCloseTo(800.0, within(1e-6));
		assertThat(scale.bedReference()).isCloseTo(800.0, within(1e-6));
		assertThat(scale.threatOf(index(1000))).isEqualTo(Threat.MEDIUM);
	}

	@Test
	void unknownAndNickedTeammatesAreLeftOutOfTheAverage() {
		ThreatScale scale = ThreatScale.of(ThreatScale.Basis.TEAM, index(900),
				java.util.Arrays.asList(null, PlayerStats.nicked("Nick", UUID.randomUUID())));

		assertThat(scale.combatReference()).isCloseTo(900.0, within(1e-6));
	}

	@Test
	void withoutMyOwnStatsTheTeamStandsInAndWithoutEitherTheAbsoluteScaleDoes() {
		assertThat(ThreatScale.of(ThreatScale.Basis.TEAM, null, List.of(index(700))).combatReference())
				.isCloseTo(700.0, within(1e-6));
		assertThat(ThreatScale.of(ThreatScale.Basis.ME, null, List.of(index(700))))
				.isEqualTo(ThreatScale.ABSOLUTE);
	}

	@Test
	void aBrandNewAccountDoesNotMakeEverybodyExtreme() {
		// Index 0 would divide by zero; a floor keeps a fresh account's scale sane.
		ThreatScale scale = ThreatScale.of(ThreatScale.Basis.ME, index(0), List.of());

		assertThat(scale.combatReference()).isEqualTo(ThreatScale.MIN_REFERENCE);
		assertThat(scale.threatOf(index(10))).isEqualTo(Threat.NONE);
		assertThat(scale.threatOf(index(40))).isEqualTo(Threat.LOW);
	}

	@Test
	void theAbsoluteScaleIsTheOldOne() {
		PlayerStats enemy = index(2000);

		assertThat(ThreatScale.ABSOLUTE.threatOf(enemy)).isEqualTo(Threat.of(enemy));
		assertThat(ThreatScale.of(ThreatScale.Basis.ABSOLUTE, index(5), List.of())).isEqualTo(ThreatScale.ABSOLUTE);
	}

	@Test
	void unknownAndNickedStayWhatTheyAre() {
		ThreatScale scale = ThreatScale.of(ThreatScale.Basis.ME, index(1000), List.of());

		assertThat(scale.threatOf(null)).isEqualTo(Threat.UNKNOWN);
		assertThat(scale.threatOf(PlayerStats.nicked("Nick", UUID.randomUUID()))).isEqualTo(Threat.NICKED);
	}

	@Test
	void theFocusDecidesWhichIndexIsRated() {
		// Fights like an average 1000-star player, breaks beds as if they were much better.
		PlayerStats enemy = rusher(1000, 3.0);
		ThreatScale scale = ThreatScale.of(ThreatScale.Basis.ME, index(1000), List.of());

		assertThat(scale.withFocus(ThreatFocus.COMBAT).threatOf(enemy)).isEqualTo(Threat.MEDIUM);
		assertThat(scale.withFocus(ThreatFocus.BEDS).threatOf(enemy)).isGreaterThan(Threat.MEDIUM);
		assertThat(scale.withFocus(ThreatFocus.BOTH).threatOf(enemy))
				.isEqualTo(scale.withFocus(ThreatFocus.BEDS).threatOf(enemy));
	}

	@Test
	void bothRatesEachWayOnItsOwnAndReportsTheWorse() {
		PlayerStats enemy = rusher(1000, 3.0);
		ThreatScale.Rating rating = ThreatScale.of(ThreatScale.Basis.ME, index(1000), List.of())
				.withFocus(ThreatFocus.BOTH).rate(enemy);

		assertThat(rating.combat()).isEqualTo(Threat.MEDIUM);
		assertThat(rating.beds()).isGreaterThan(Threat.MEDIUM);
		assertThat(rating.overall()).isEqualTo(rating.beds());
		assertThat(rating.worseAtBeds()).isTrue();
	}

	@Test
	void theBedsAreMeasuredAgainstMyOwnBeds() {
		// Against a rusher of the same kind the same enemy is an even match at the beds.
		PlayerStats enemy = rusher(1000, 3.0);
		ThreatScale scale = ThreatScale.of(ThreatScale.Basis.ME, rusher(1000, 3.0), List.of()).withFocus(ThreatFocus.BEDS);

		assertThat(scale.threatOf(enemy)).isEqualTo(Threat.MEDIUM);
	}

	@Test
	void theFocusDefaultsToBothAndSurvivesTheSensitivity() {
		ThreatScale scale = ThreatScale.of(ThreatScale.Basis.ME, index(1000), List.of());

		assertThat(scale.focus()).isEqualTo(ThreatFocus.BOTH);
		assertThat(scale.withFocus(ThreatFocus.BEDS).withSensitivity(2.0).focus()).isEqualTo(ThreatFocus.BEDS);
		assertThat(scale.withFocus(null).focus()).isEqualTo(ThreatFocus.BOTH);
	}

	@Test
	void theScoreSortsByWhatTheFocusRates() {
		PlayerStats rusher = rusher(1000, 3.0);
		PlayerStats fighter = index(1500);
		ThreatScale scale = ThreatScale.ABSOLUTE;

		assertThat(scale.withFocus(ThreatFocus.COMBAT).score(fighter))
				.isGreaterThan(scale.withFocus(ThreatFocus.COMBAT).score(rusher));
		assertThat(scale.withFocus(ThreatFocus.BEDS).score(rusher))
				.isGreaterThan(scale.withFocus(ThreatFocus.BEDS).score(fighter));
		assertThat(scale.score(null)).isNegative();
	}

	@Test
	void unknownAndNickedPlayersHaveARatingWithoutLevels() {
		ThreatScale.Rating nick = ThreatScale.ABSOLUTE.rate(PlayerStats.nicked("Nick", UUID.randomUUID()));

		assertThat(nick.overall()).isEqualTo(Threat.NICKED);
		assertThat(nick.combat()).isEqualTo(Threat.NICKED);
		assertThat(ThreatScale.ABSOLUTE.rate(null).overall()).isEqualTo(Threat.UNKNOWN);
	}
}
