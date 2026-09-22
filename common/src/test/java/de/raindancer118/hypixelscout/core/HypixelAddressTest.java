package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HypixelAddressTest {
	@Test
	void theUsualAddressesAreHypixel() {
		assertThat(HypixelAddress.matches("mc.hypixel.net")).isTrue();
		assertThat(HypixelAddress.matches("hypixel.net")).isTrue();
		assertThat(HypixelAddress.matches("HYPIXEL.NET:25565")).isTrue();
		assertThat(HypixelAddress.matches("  stuck.hypixel.net ")).isTrue();
	}

	@Test
	void aLookalikeIsNot() {
		assertThat(HypixelAddress.matches("nothypixel.net")).isFalse();
		assertThat(HypixelAddress.matches("hypixel.net.evil.com")).isFalse();
		assertThat(HypixelAddress.matches("localhost")).isFalse();
		assertThat(HypixelAddress.matches(null)).isFalse();
	}
}
