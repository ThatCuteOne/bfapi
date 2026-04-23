package dev.vuis.bfapi.main;

import dev.vuis.bfapi.cloud.BfConnectionManager;
import dev.vuis.bfapi.util.EnvironmentConfigs;
import dev.vuis.bfapi.http.BfApiChannelInitializer;
import dev.vuis.bfapi.http.BfApiInboundHandler;
import dev.vuis.bfapi.util.PersistentDiskStorage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ApiMain {
	private static BfConnectionManager BFConnectionManager;
	private static JavaAuthManager mcAuth;
	private static @Nullable BfApiInboundHandler BfAPIinboundHandler;
	
	@SneakyThrows
	static void main() {
		authenticate();

		log.info("starting HTTP server");
		BfApiInboundHandler inboundHandler = new BfApiInboundHandler(EnvironmentConfigs.BF_UCD_REFRESH_SECRET);
		startHttpServer(inboundHandler);
		BFConnectionManager = new BfConnectionManager(mcAuth,BfAPIinboundHandler);
		BFConnectionManager.init();

	}
	private static void startHttpServer(BfApiInboundHandler inboundHandler) {
		BfAPIinboundHandler = inboundHandler;
		ServerBootstrap bootstrap = new ServerBootstrap()
			.group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()))
			.channel(NioServerSocketChannel.class)
			.childHandler(new BfApiChannelInitializer(inboundHandler));

		bootstrap.bind(EnvironmentConfigs.HOST_PORT).syncUninterruptibly();
	}


	private static void authenticate() throws IOException , InterruptedException, TimeoutException {
		String storedToken = PersistentDiskStorage.getInstance().getMSRefreshToken();
		if (storedToken == null) {
			log.error("could not load Refresh token from file Please Authenticate");
			mcAuth = JavaAuthManager.create(MinecraftAuth.createHttpClient("bfapi/1.0-SNAPSHOT"))
				.login(DeviceCodeMsaAuthService::new, (Consumer<MsaDeviceCode>) code -> log.info("microsoft auth URL: {}", code.getDirectVerificationUri()));
				PersistentDiskStorage.getInstance().setRefreshToken(mcAuth.getMsaToken().getUpToDate().getRefreshToken());
			}
		else {
			log.info("loaded Stored Refresh token from file");
			mcAuth = JavaAuthManager.create(MinecraftAuth.createHttpClient("bfapi/1.0-SNAPSHOT")).login(storedToken);
		}

		log.info("retrieving profile");
		MinecraftProfile mcProfile = mcAuth.getMinecraftProfile().getUpToDate();
		log.info("authenticated as {} ({})", mcProfile.getName(), mcProfile.getId());




	}
}
