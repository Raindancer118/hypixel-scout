package de.raindancer118.hypixelscout.core;

import java.util.Locale;

/**
 * Whether a server address is Hypixel, for the features that must never fire anywhere else.
 *
 * <p>Matched on the host name, not on a substring: {@code nothypixel.net} is somebody else's server
 * and a stray {@code /play} there is exactly what the check exists to prevent.
 */
public final class HypixelAddress {
	private HypixelAddress() {
	}

	public static boolean matches(String address) {
		if (address == null) {
			return false;
		}

		String host = address.trim().toLowerCase(Locale.ROOT);
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(':') == colon) {
			host = host.substring(0, colon);
		}

		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}

		return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
	}
}
