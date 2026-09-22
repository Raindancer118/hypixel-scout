package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.core.Roster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * What the tab list says, as roster entries. The roster itself decides which of them are players.
 *
 * <p>Replaceable, so the client game test can fill a game with sixteen players who exist only in
 * the test; the mod always reads the real tab list.
 */
public final class TabListReader {
	private static Supplier<Collection<Roster.Member>> source = TabListReader::read;

	private TabListReader() {
	}

	public static Collection<Roster.Member> current() {
		return source.get();
	}

	/** For the client game test only. {@code null} goes back to the real tab list. */
	public static void replaceForTest(Supplier<Collection<Roster.Member>> replacement) {
		source = replacement == null ? TabListReader::read : replacement;
	}

	private static Collection<Roster.Member> read() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return List.of();
		}

		List<Roster.Member> members = new ArrayList<>();
		for (PlayerInfo info : connection.getListedOnlinePlayers()) {
			var profile = info.getProfile();
			if (profile != null) {
				members.add(new Roster.Member(profile.name(), profile.id()));
			}
		}

		return members;
	}
}
