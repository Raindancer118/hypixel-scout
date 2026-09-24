package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.core.Roster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import com.mojang.authlib.GameProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * What the tab list says, as roster entries. The roster itself decides which of them are players.
 *
 * <p>Replaceable, so a future client game test can fill a game with fake players who exist only in
 * the test; the mod always reads the real tab list.
 */
public final class TabListReader {
	private static Supplier<Collection<Roster.Member>> source = TabListReader::read;

	private TabListReader() {
	}

	public static Collection<Roster.Member> current() {
		return source.get();
	}

	/** For a client game test only. {@code null} goes back to the real tab list. */
	public static void replaceForTest(Supplier<Collection<Roster.Member>> replacement) {
		source = replacement == null ? TabListReader::read : replacement;
	}

	private static Collection<Roster.Member> read() {
		Minecraft client = Minecraft.getMinecraft();
		NetHandlerPlayClient connection = client.getNetHandler();
		if (connection == null) {
			return Collections.emptyList();
		}

		List<Roster.Member> members = new ArrayList<Roster.Member>();
		for (NetworkPlayerInfo info : connection.getPlayerInfoMap()) {
			GameProfile profile = info.getGameProfile();
			if (profile != null && profile.getName() != null && profile.getId() != null) {
				members.add(new Roster.Member(profile.getName(), profile.getId()));
			}
		}

		return members;
	}
}
