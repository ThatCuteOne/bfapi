package dev.vuis.bfapi.util;

import dev.vuis.bfapi.util.config.BuildInfo;

import java.net.InetSocketAddress;

public final class EnvironmentConfigs {
    public static final InetSocketAddress BF_CLOUD_ADDRESS = new InetSocketAddress("cloud.blockfrontmc.com", 1924);
    public static final int HOST_PORT = EnvUtil.getOrDefault("PORT",8080);
	public static final String  HTTP_USER_AGENT = EnvUtil.getOrDefault("HTTP_USER_AGENT", "bfapi/1.0-SNAPSHOT");

	public static final byte[] BF_HARDWARE_ID = EnvUtil.getOrThrow("BF_HARDWARE_ID", Util::parseHexArray);
	public static final String BF_UCD_REFRESH_SECRET = EnvUtil.getOrThrow("BF_UCD_REFRESH_SECRET");
	public static final String PERSISTENT_STORAGE_LOCATION = EnvUtil.getOrDefault("PERSISTENT_STORAGE_LOCATION", "persistent_data.json");
	public static final String BF_VERSION = EnvUtil.getOrDefault("BF_VERSION", BuildInfo. getBlockfrontVersion());
	public static final String BF_VERSION_HASH = EnvUtil.getOrDefault("BF_VERSION_HASH",BuildInfo.getBlockfrontHash());
	public static final String BF_PLAYER_LIST_FILE = EnvUtil.getOrDefault("BF_PLAYER_LIST_FILE","players.txt");
	public static final boolean BF_UCD_WRITE_FILTERED_PLAYERS = EnvUtil.getOrDefault("BF_UCD_WRITE_FILTERED_PLAYERS", false);
	public static final boolean BF_SCRAPE_FRIENDS = EnvUtil.getOrDefault("BF_SCRAPE_FRIENDS", false);

	public static final int BF_SCRAPE_FRIENDS_DEPTH = EnvUtil.getOrDefault("BF_SCRAPE_FRIENDS_DEPTH", 2);
	public static final int MAX_RECONNECT_ATTEMPTS = EnvUtil.getOrDefault("MAX_RECONNECT_ATTEMPTS", 10);
	public static final int RECONNECT_DELAY_SECONDS = EnvUtil.getOrDefault("RECONNECT_DELAY_SECONDS", 30);
}
