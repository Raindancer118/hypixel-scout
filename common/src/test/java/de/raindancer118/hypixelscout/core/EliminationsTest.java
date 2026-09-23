package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EliminationsTest {
	@Test
	void theVictimOfAFinalKillIsTheFirstName() {
		assertThat(Eliminations.finalKillVictim("Ashenvale was knocked into the void by Lanternfish. FINAL KILL!"))
				.isEqualTo("Ashenvale");
		assertThat(Eliminations.finalKillVictim("Kettle_Drum fell into the void. FINAL KILL!"))
				.isEqualTo("Kettle_Drum");
	}

	@Test
	void anOrdinaryDeathIsNoElimination() {
		assertThat(Eliminations.finalKillVictim("Ashenvale was knocked into the void by Lanternfish.")).isNull();
	}

	@Test
	void somebodyTypingFinalKillInChatEliminatesNobody() {
		assertThat(Eliminations.finalKillVictim("[MVP+] Sundial: Ashenvale FINAL KILL!")).isNull();
		assertThat(Eliminations.finalKillVictim("Party > [VIP] quietfox: gg FINAL KILL!")).isNull();
	}

	@Test
	void eliminationsAreRememberedUntilTheNextGame() {
		Eliminations out = new Eliminations();
		out.record("Ashenvale fell into the void. FINAL KILL!");

		assertThat(out.isOut("ashenvale")).isTrue();
		assertThat(out.isOut("quietfox")).isFalse();

		out.reset();
		assertThat(out.isOut("Ashenvale")).isFalse();
	}

	@Test
	void offNeverRequeues() {
		assertThat(Eliminations.requeueDue(RequeueMode.OFF, "Me", List.of(), name -> true)).isFalse();
	}

	@Test
	void selfRequeuesOnceIAmOutWhateverTheParty() {
		assertThat(Eliminations.requeueDue(RequeueMode.SELF, "Me", List.of("Mate"), name -> name.equals("Me")))
				.isTrue();
		assertThat(Eliminations.requeueDue(RequeueMode.SELF, "Me", List.of(), name -> false)).isFalse();
	}

	@Test
	void partyWaitsForEverybodyInTheGame() {
		assertThat(Eliminations.requeueDue(RequeueMode.PARTY, "Me", List.of("Mate"), name -> name.equals("Me")))
				.isFalse();
		assertThat(Eliminations.requeueDue(RequeueMode.PARTY, "Me", List.of("Mate"), name -> true)).isTrue();
	}

	@Test
	void withoutAPartyThePartyModeIsJustMe() {
		assertThat(Eliminations.requeueDue(RequeueMode.PARTY, "Me", List.of(), name -> name.equals("Me"))).isTrue();
	}
}
