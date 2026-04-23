package dev.vuis.bfapi.cloud;

import com.boehmod.bflib.cloud.common.RequestType;
import com.boehmod.bflib.cloud.connection.ConnectionStatus;
import com.boehmod.bflib.cloud.packet.common.PacketClientRequest;
import com.boehmod.bflib.cloud.packet.common.requests.PacketRequestedFriends;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.vuis.bfapi.cloud.unofficial.UnofficialCloudData;
import dev.vuis.bfapi.http.BfApiInboundHandler;
import dev.vuis.bfapi.util.EnvironmentConfigs;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;



@Slf4j
public class BfConnectionManager {
    private static final ScheduledExecutorService refreshExecutor = Executors.newSingleThreadScheduledExecutor();
    private final InetSocketAddress cloudAdress = EnvironmentConfigs.BF_CLOUD_ADDRESS;
    private final BfApiInboundHandler BfAPIinboundHandler;
    private final Set<UUID> ucdPlayers;

    public record BlockfrontVersionData(String ID, String HASH) {}
    private static @Nullable ScheduledFuture<?> cloudDataRefreshFuture = null;
    private static @Nullable CompletableFuture<Set<UUID>> friendScrapeFuture = null;
    private UnofficialCloudData ucd;
    private final JavaAuthManager mcAuth;
    @Getter
    private BfConnection currentConnection;
    public BfConnectionManager(JavaAuthManager mcAuth,BfApiInboundHandler inboundHandler) {
        this.mcAuth = mcAuth;
        this.ucdPlayers = loadUcdPlayers();
        this.BfAPIinboundHandler = inboundHandler;
    }
    public void init() {
        if (EnvironmentConfigs.BF_SCRAPE_FRIENDS) {
            BfCloudPacketHandlers.registerPacketHandler(PacketRequestedFriends.class,(packet,connection) -> this.handleFriendScrapePacket(packet));
        }
        BfCloudPacketHandlers.register();
        connect();
    }
    private void connect() {
        BlockfrontVersionData versionData;
        try {
            log.info("Fetching Blockfront Version data from Cloud");
            versionData = getBlockfrontVersionData();
            log.info("Sucessfully fetched Blockfront Version data");

        } catch (IOException | InterruptedException e) {
            log.error("Failed to Fetch Blockfront Version Data from Cloud using enviroment Variables : ", e);
            versionData = new BlockfrontVersionData(
                    EnvironmentConfigs.BF_VERSION,
                    EnvironmentConfigs.BF_VERSION_HASH
            );
        }

        BfConnection connection = new BfConnection(
                cloudAdress,
                mcAuth, versionData, EnvironmentConfigs.BF_HARDWARE_ID
        );
        connection.connect();
        connection.addStatusListener(this::onConnectionStatusChanged);
        connection.addConnectionFailureListener(this::onConnectionFail);
        this.currentConnection = connection;
        this.ucd = new UnofficialCloudData(ucdPlayers, connection.dataCache, EnvironmentConfigs.BF_UCD_WRITE_FILTERED_PLAYERS);
        BfAPIinboundHandler.connectionReference.set(connection);
        BfAPIinboundHandler.ucdReference.set(ucd);
    }

    private static Set<UUID> loadUcdPlayers() {
        try {
            return Arrays.stream(Files.readString(Path.of(EnvironmentConfigs.BF_PLAYER_LIST_FILE)).split("\n"))
                    .map(UUID::fromString)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.error("Could not read file, using empty set: {}", e.getMessage());
            return new HashSet<>();
        }
    }


    private void onConnectionStatusChanged(ConnectionStatus status) {
        switch (status) {
            case CONNECTED_VERIFIED -> {
                if (EnvironmentConfigs.BF_SCRAPE_FRIENDS) {
                    new Thread(() -> friendScraperThread(ucdPlayers), "friend scraper").start();
                } else {
                    ucd.startRefresh();
                    cloudDataRefreshFuture = refreshExecutor.scheduleAtFixedRate(
                            this::refreshCloudData,
                            0, 60, TimeUnit.SECONDS
                    );
                }
            }
            case CLOSED -> {
                if (cloudDataRefreshFuture != null) {
                    cloudDataRefreshFuture.cancel(false);
                    cloudDataRefreshFuture = null;
                }
            }
        }


    }
    @SneakyThrows
    private void friendScraperThread( Set<UUID> startFront) {
        Thread.sleep(2000);

        log.info("started friend scraper");

        MutableGraph<UUID> friendGraph = GraphBuilder.undirected().build();
        Set<UUID> scraped = new HashSet<>();
        Set<UUID> front = startFront;

        for (int depth = 1; depth <= EnvironmentConfigs.BF_SCRAPE_FRIENDS_DEPTH; depth++) {
            int num = 0;
            Set<UUID> nextFront = new HashSet<>();

            for (UUID user : front) {
                num++;

                log.info("(depth {}, found {}) {}/{}", depth, friendGraph.nodes().size(), num, front.size());

                if (!scraped.add(user)) {
                    log.info("skipped");
                    continue;
                }

                friendScrapeFuture = new CompletableFuture<>();
                currentConnection.sendPacket(new PacketClientRequest(
                        EnumSet.noneOf(RequestType.class),
                        ObjectList.of(Map.entry(user, EnumSet.of(RequestType.PLAYER_FRIENDS)))
                ));

                Set<UUID> friends = friendScrapeFuture.join();
                friendScrapeFuture = null;

                for (UUID friend : friends) {
                    friendGraph.putEdge(user, friend);

                    if (!scraped.contains(friend)) {
                        nextFront.add(friend);
                    }
                }

                scraped.add(user);

                Thread.sleep(1000);
            }

            front = nextFront;
        }

        log.info("total players: {}", friendGraph.nodes().size());
        log.info("serializing");

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Path.of("scraped_friends.txt")))) {
            for (UUID user : friendGraph.nodes()) {
                writer.println(user);
            }
        }

        log.info("done");
    }

    private void handleFriendScrapePacket(PacketRequestedFriends packet) {
        if (friendScrapeFuture == null) {
            log.warn("unexpected PacketRequestedFriends (friend scrape mode)");
            return;
        }

        friendScrapeFuture.complete(packet.friends());
    }
    private void refreshCloudData() {
        if (!currentConnection.isConnectedAndVerified()) {
            return;
        }

        BfCloudData cloudData;
        try {
            cloudData = currentConnection.dataCache.cloudData.get().get(10, TimeUnit.SECONDS).value();
        } catch (InterruptedException | TimeoutException e) {
            return;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        currentConnection.dataCache.playerData.request(cloudData.playerScores().stream().map(ObjectIntImmutablePair::left).collect(Collectors.toUnmodifiableSet()), true);
        currentConnection.dataCache.clanData.request(cloudData.clanScores().stream().map(ObjectIntImmutablePair::left).collect(Collectors.toUnmodifiableSet()), true);
    }

    public static BlockfrontVersionData getBlockfrontVersionData() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://cloud.blockfrontmc.com:8001/api/v2/?type=version"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Gson gson = new Gson();
        JsonObject jsonParsed = gson.fromJson(response.body(), JsonObject.class);

        return new BlockfrontVersionData(
                jsonParsed.get("version").getAsString(),
                jsonParsed.get("hash").getAsString()
        );
    }
    private void onConnectionFail() {
        this.currentConnection.disconnect("reconnecting", true);
        log.info("Reinitializing Connection To Cloud");
        connect();
    }




}
