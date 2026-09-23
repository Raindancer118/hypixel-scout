package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AllegianceTest {
	@Test
	void aDifferentKnownTeamIsAnEnemy() {
		assertThat(Allegiance.of("Red", "Blue")).isEqualTo(Allegiance.ENEMY);
	}

	@Test
	void theSameTeamIsAnAllyWhateverTheCase() {
		assertThat(Allegiance.of("Red", "Red")).isEqualTo(Allegiance.ALLY);
		assertThat(Allegiance.of("Red", "red")).isEqualTo(Allegiance.ALLY);
	}

	@Test
	void withoutMyOwnTeamNobodyCanBeCalledAnEnemy() {
		// The waiting lobby, or a scoreboard the mod cannot read: better no popup than one for a teammate.
		assertThat(Allegiance.of("", "Blue")).isEqualTo(Allegiance.UNKNOWN);
		assertThat(Allegiance.of(null, "Blue")).isEqualTo(Allegiance.UNKNOWN);
	}

	@Test
	void somebodyWithoutATeamIsNeither() {
		assertThat(Allegiance.of("Red", "")).isEqualTo(Allegiance.UNKNOWN);
		assertThat(Allegiance.of("Red", null)).isEqualTo(Allegiance.UNKNOWN);
	}
}
