# Builder: build the distribution using JDK 25
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

COPY . /workspace

RUN chmod +x ./gradlew \
 && ./gradlew --no-daemon installDist -x test

# Runtime image: lightweight JRE 25
FROM eclipse-temurin:25-jre
WORKDIR /opt/bfapi

# Copy the contents of whatever folder installDist generated into our working directory
COPY --from=builder /workspace/build/install/* /opt/bfapi/

ENV PORT=8080 \
    MS_CLIENT_ID=00000000402B5328 \
    MS_REDIRECT_HOST=https://login.live.com/oauth20_desktop.srf \
    MS_PASTE_REDIRECT=true \
    BF_VERSION=0.8.0.2b \
    BF_VERSION_HASH=d937c0f7cf81ff6733487acda4bc4c06 \
    BF_PLAYER_LIST_FILE=/opt/bfapi/players.txt\
    BF_HARDWARE_ID=2af8d79cef89c08d376f14ac1459d5a39b3be5577eeaa6219fcb901701a8d233 \
    BF_UCD_REFRESH_SECRET=meoiw
    

EXPOSE ${PORT}

# Since we copied the contents directly into /opt/bfapi, the bin directory is right there.
# We use a shell form here to allow wildcard expansion for the executable name, 
# just in case the script isn't named exactly 'bfapi'.
ENTRYPOINT ["sh", "-c", "exec /opt/bfapi/bin/*"]