package dev.vuis.bfapi.util.config;

import lombok.Getter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Properties;

class CompileData {
    @Getter
    private static String blockfrontVersion;
    @Getter
    private static String blockfrontLibVersion;
    @Getter
    private static Instant buildTime;

    public CompileData(String bfVersion,String bfLibVersion,Instant timeAtBuild) {
        buildTime = timeAtBuild;
        blockfrontLibVersion = bfLibVersion;
        blockfrontVersion = bfVersion;
    }
}

public class BuildInfo {
    @Getter
    private static String blockfrontHash;
    @Getter
    private static String blockfrontVersion;
    @Getter
    private static CompileData compileData;

    static {
        loadData();
    }

    private static void loadData() {
        Properties props = new Properties();
        try (InputStream input = new FileInputStream("build.properties")) {
            // Load the properties file
            props.load(input);

            // Access values
            String libVersion = props.getProperty("blockfront.compile.lib_version");
            String compileVersion = props.getProperty("blockfront.compile.version");
            String version = props.getProperty("blockfront.version");
            String hash = props.getProperty("blockfront.version.hash");
            String buildTime = props.getProperty("build.time");

            compileData = new CompileData(
                compileVersion,
                libVersion,
                Instant.parse(buildTime)
            );
            blockfrontHash = hash;
            blockfrontVersion = version;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
