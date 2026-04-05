package dev.vuis.bfapi.util;

import dev.vuis.bfapi.util.config.BuildInfo;

import java.net.InetSocketAddress;
import java.util.function.Function;

public final class EnvironmentConfigs {
    public static final InetSocketAddress BF_CLOUD_ADDRESS = new InetSocketAddress("cloud.blockfrontmc.com", 1924);
    public static final int HOST_PORT = Util.getEnvOrElse("PORT",8080,Integer::parseInt);

	public static final byte[] BF_HARDWARE_ID = Util.parseHexArray(Util.getEnvOrThrow("BF_HARDWARE_ID"));
	public static final String BF_UCD_REFRESH_SECRET = Util.getEnvOrThrow("BF_UCD_REFRESH_SECRET");

	public static final String PERSISTENT_STORAGE_LOCATION = Util.getEnvOrElse("PERSISTENT_STORAGE_LOCATION", "persistent_data.json", Function.identity());
	public static final String BF_VERSION = Util.getEnvOrElse("BF_VERSION", BuildInfo.getBlockfrontVersion(),String::new);
	public static final String BF_VERSION_HASH = Util.getEnvOrElse("BF_VERSION_HASH",BuildInfo.getBlockfrontHash(),String::new);
	public static final String BF_PLAYER_LIST_FILE = Util.getEnvOrElse("BF_PLAYER_LIST_FILE","players.txt",Function.identity());
	public static final boolean BF_UCD_WRITE_FILTERED_PLAYERS = Util.getEnvOrElse("BF_UCD_WRITE_FILTERED_PLAYERS", false,Boolean::parseBoolean);
	public static final boolean BF_SCRAPE_FRIENDS = Util.getEnvOrElse("BF_SCRAPE_FRIENDS", false,Boolean::parseBoolean);

	public static final int BF_SCRAPE_FRIENDS_DEPTH = Util.getEnvOrElse("BF_SCRAPE_FRIENDS_DEPTH", 2,Integer::parseInt);
	public static final int MAX_RECONNECT_ATTEMPTS = Util.getEnvOrElse("MAX_RECONNECT_ATTEMPTS", 10,Integer::parseInt);
	public static final int RECONNECT_DELAY_SECONDS = Util.getEnvOrElse("RECONNECT_DELAY_SECONDS", 30,Integer::parseInt);
}
