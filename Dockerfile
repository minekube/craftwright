FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        libasound2 \
        libatk-bridge2.0-0 \
        libatk1.0-0 \
        libcairo2 \
        libcups2 \
        libdbus-1-3 \
        libdrm2 \
        libgbm1 \
        libgtk-3-0 \
        libnss3 \
        libx11-6 \
        libxcomposite1 \
        libxdamage1 \
        libxext6 \
        libxfixes3 \
        libxrandr2 \
        libxtst6 \
        xauth \
        xvfb \
    && rm -rf /var/lib/apt/lists/*

ENV CRAFTLESS_WORKSPACE=/var/lib/craftless
ENV CRAFTLESS_PORT=8080
ENV CRAFTLESS_FABRIC_DRIVER_MOD=/opt/craftless/mods/craftless-driver-fabric.jar

COPY build/docker/craftless/ /opt/craftless/
COPY docker/entrypoint.sh /usr/local/bin/craftless-entrypoint

RUN chmod +x /usr/local/bin/craftless-entrypoint \
    && mkdir -p /var/lib/craftless

VOLUME ["/var/lib/craftless"]
EXPOSE 8080

ENTRYPOINT ["craftless-entrypoint"]
