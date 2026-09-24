package de.raindancer118.hypixelscout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Proves the {@code generateVersion} Gradle task actually wrote what {@code gradle/shared
 * .properties} declares, and that {@link HypixelScout#VERSION} still reads it back as the same
 * compile-time constant Forge needs.
 */
class HypixelScoutVersionTest {

	@Test
	void generatedVersionMatchesGradleModVersion() {
		String expected = System.getProperty("hypixelscout.expectedVersion");
		assertThat(expected).as("test task must set -Dhypixelscout.expectedVersion").isNotBlank();
		assertThat(HypixelScoutVersion.VERSION).isEqualTo(expected);
	}

	@Test
	void modVersionConstantMatchesGeneratedVersion() {
		assertThat(HypixelScout.VERSION).isEqualTo(HypixelScoutVersion.VERSION);
	}
}
