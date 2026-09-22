package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RanksTest {
	@Test
	void eachRankReadsInHypixelsOwnColour() {
		assertThat(Ranks.colour(null)).isEqualTo("§7");
		assertThat(Ranks.colour("VIP")).isEqualTo("§a");
		assertThat(Ranks.colour("VIP_PLUS")).isEqualTo("§a");
		assertThat(Ranks.colour("MVP")).isEqualTo("§b");
		assertThat(Ranks.colour("MVP_PLUS")).isEqualTo("§b");
		assertThat(Ranks.colour("SUPERSTAR")).isEqualTo("§6");
		assertThat(Ranks.colour("YOUTUBER")).isEqualTo("§c");
		assertThat(Ranks.colour("ADMIN")).isEqualTo("§c");
		assertThat(Ranks.colour("GAME_MASTER")).isEqualTo("§2");
	}

	@Test
	void theTagIsWhatChatWouldShow() {
		assertThat(Ranks.tag(null)).isEmpty();
		assertThat(Ranks.tag("VIP_PLUS")).isEqualTo("§a[VIP§6+§a]");
		assertThat(Ranks.tag("MVP_PLUS")).isEqualTo("§b[MVP§c+§b]");
		assertThat(Ranks.tag("SUPERSTAR")).isEqualTo("§6[MVP§c++§6]");
		assertThat(Ranks.tag("YOUTUBER")).isEqualTo("§c[§fYOUTUBE§c]");
		assertThat(Ranks.tag("GAME_MASTER")).isEqualTo("§2[GM]");
	}
}
