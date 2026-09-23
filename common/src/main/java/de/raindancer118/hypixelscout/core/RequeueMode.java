package de.raindancer118.hypixelscout.core;

/** When the next game is joined by itself. */
public enum RequeueMode {
	/** Never. */
	OFF,
	/** As soon as the player is out. */
	SELF,
	/** Once the player and every party member in the same game are out. */
	PARTY
}
