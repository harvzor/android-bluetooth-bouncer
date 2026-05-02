# syntax=docker/dockerfile:1

# =============================================================================
# Stage 1: builder
# =============================================================================
FROM eclipse-temurin:17-jdk-jammy AS builder

# Use bash for all RUN commands (required for ${VAR^} capitalisation)
SHELL ["/bin/bash", "-c"]

# Install unzip + wget (needed for cmdline-tools download)
RUN apt-get update -q && \
    apt-get install -y --no-install-recommends unzip wget && \
    rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------------
# Layer: Android SDK cmdline-tools
# Pinned to a specific version for reproducibility.
# Update ANDROID_CMDLINE_TOOLS_VERSION when bumping SDK tooling.
# ---------------------------------------------------------------------------
ARG ANDROID_CMDLINE_TOOLS_VERSION=11076708
RUN wget -q \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
        -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extracted && \
    mkdir -p /opt/android-sdk/cmdline-tools && \
    mv /tmp/cmdline-tools-extracted/cmdline-tools /opt/android-sdk/cmdline-tools/latest && \
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-extracted

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

# Accept SDK licenses and install required SDK packages
RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;35.0.0"

# ---------------------------------------------------------------------------
# Layer: Gradle wrapper + build config files
# Copied before source so this layer is only invalidated when build config
# changes (not on every source edit).
# ---------------------------------------------------------------------------
WORKDIR /app

COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY app/build.gradle.kts app/build.gradle.kts

# Stub AndroidManifest.xml so AGP can configure during dependency resolution
RUN mkdir -p app/src/main && \
    printf '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="net.harveywilliams.bluetoothbouncer" />\n' \
        > app/src/main/AndroidManifest.xml && \
    chmod +x gradlew

# ---------------------------------------------------------------------------
# Layer: Gradle + Maven dependency cache
# Pre-warms ~/.gradle/caches with all dependency jars.
# Only re-runs when build config files above change.
# ---------------------------------------------------------------------------
RUN ./gradlew dependencies --no-daemon

# ---------------------------------------------------------------------------
# Layer: full source + assemble
# Invalidated on every source change, but layers above are cached.
# ---------------------------------------------------------------------------
COPY app/ app/

ARG BUILD_TYPE=debug
RUN ./gradlew assemble${BUILD_TYPE^} --no-daemon && \
    mkdir -p /out && \
    find app/build/outputs/apk -name "*.apk" -exec cp {} /out/ \;

# =============================================================================
# Stage 2: export
# Minimal stage containing only the APK(s).
# Used with: docker build --output=out .
# =============================================================================
FROM scratch AS export
COPY --from=builder /out/ /
