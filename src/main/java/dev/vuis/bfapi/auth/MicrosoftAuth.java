package dev.vuis.bfapi.auth;

import com.google.gson.JsonObject;

import dev.vuis.bfapi.data.AuthToken;
import dev.vuis.bfapi.util.PersistentDiskStorage;
import dev.vuis.bfapi.util.Util;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@RequiredArgsConstructor
public class MicrosoftAuth {
	public static final String XBOX_LIVE_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

	private static final Random SECURE_RANDOM = new SecureRandom();
	private static final Base64.Encoder BASE_64_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private PersistentDiskStorage persistentStorage = PersistentDiskStorage.getInstance();
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final Object refreshLock = new Object();

	private final String clientId;
	private final String clientSecret;
	private final String redirectUri;

	private volatile AuthToken accessToken = null;

	private volatile AuthToken refreshToken = new AuthToken(
					PersistentDiskStorage.getInstance().getMSRefreshToken()
				);

	public String getAuthUri(@NotNull String scope, String state) {
		return "https://login.live.com/oauth20_authorize.srf" +
			"?client_id=" + Util.urlEncode(clientId) +
			"&response_type=code" +
			"&redirect_uri=" + Util.urlEncode(redirectUri) +
			"&response_mode=query" +
			"&scope=" + Util.urlEncode(scope) +
			(state == null ? "" : "&state=" + Util.urlEncode(state));
	}
	public AuthToken redeemCode(@NotNull AuthToken code) throws IOException, InterruptedException {
		return redeemCode(code, false);
	}
	public AuthToken redeemCode(@NotNull AuthToken code, boolean isRefresh ) throws IOException, InterruptedException {
		log.info("redeeming microsoft authentication code");

		String uri = "https://login.live.com/oauth20_token.srf";

		Map<String, String> parameters = new LinkedHashMap<>();
		parameters.put("client_id", clientId);
		parameters.put("redirect_uri", redirectUri);
		if (clientSecret != null) {
        	parameters.put("client_secret", clientSecret);
    	}

		if (isRefresh) {
			parameters.put("grant_type", "refresh_token");
			parameters.put("refresh_token", code.getToken());
			parameters.put("scope", XBOX_LIVE_SCOPE);
    	} else {
			parameters.put("grant_type", "authorization_code");
			parameters.put("code", code.getToken());
		}

		String body = Util.buildFormUrlEncodedBody(parameters);
		log.info(body);
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(uri))
			.header(HttpHeaderNames.CONTENT_TYPE.toString(), "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (!Util.isSuccess(response.statusCode())) {
			throw new InterruptedException("code redeem failed:\n" + response.body());
		}
		JsonObject json = Util.COMPACT_GSON.fromJson(response.body(), JsonObject.class);

		Instant expiresIn = Instant.now().plusSeconds(
			Math.max(json.get("expires_in").getAsLong() - 10, 0)
		);
		log.info("Redeemed microsoft authentication code successfully");
		String refreshTokenStr = refreshToken.getToken();
		if (!isRefresh)
			refreshTokenStr = json.get("refresh_token").getAsString();
		String accessTokenStr = json.get("access_token").getAsString();

		refreshToken = new AuthToken(refreshTokenStr);
		persistentStorage.setRefreshToken(refreshToken.getToken());
		accessToken = new AuthToken(accessTokenStr, expiresIn);
		return accessToken;
	}

	public AuthToken getTokenOrRefresh() throws IOException, InterruptedException {
		if ( accessToken == null || accessToken.isExpired() ) {
			if (refreshToken == null)
				throw new IllegalStateException("not authorized yet");
			synchronized (refreshLock) {
				log.info("refreshing expired microsoft access token");
				return redeemCode(refreshToken, true);
			}
		}
		return accessToken;
		
	}

	public static String randomState() {
		byte[] bytes = new byte[16];
		SECURE_RANDOM.nextBytes(bytes);
		return BASE_64_ENCODER.encodeToString(bytes);
	}
}
