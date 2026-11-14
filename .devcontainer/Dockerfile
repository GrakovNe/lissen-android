# Build and test environment for Lissen: JDK 25 (as CI), the Android SDK, and a headless
# emulator that runs on the host's KVM.
FROM docker.io/library/eclipse-temurin:25-jdk AS jdk

FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

ARG CMDLINE_TOOLS_VERSION=13114758
ARG SYSTEM_IMAGE="system-images;android-34;google_apis;x86_64"

ENV JAVA_HOME=/opt/java/openjdk \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_AVD_SYSTEM_IMAGE=${SYSTEM_IMAGE}
ENV PATH=${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}

COPY --from=jdk /opt/java/openjdk ${JAVA_HOME}

# unzip/curl/jq for the SDK and the scripts, ffmpeg for the sample audiobook,
# the rest is what the emulator binary links against even without a window
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      unzip curl jq ffmpeg \
      libpulse0 libgl1 libegl1 libnss3 libnspr4 libxcomposite1 libxcursor1 libxdamage1 \
      libxi6 libxrandr2 libxtst6 libxkbfile1 libxkbcommon0 libasound2t64 libdrm2 libgbm1 \
 && rm -rf /var/lib/apt/lists/*

# owned by the container user, so Gradle can still fetch an SDK package it finds missing
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
 && curl -fsSL -o /tmp/cmdline-tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
 && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
 && mv /tmp/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
 && rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip \
 && chown -R vscode:vscode ${ANDROID_HOME}

USER vscode

RUN yes | sdkmanager --licenses > /dev/null \
 && sdkmanager --install "platform-tools" "emulator" "${SYSTEM_IMAGE}" \
 # compile-time packages are best effort: AGP downloads whatever is still missing on the first build
 && (sdkmanager --install "platforms;android-37.0" "build-tools;37.0.0" \
     || echo "compile SDK packages not preinstalled; Gradle will download them")

# copied up into the named volumes on first use, so they belong to vscode
RUN mkdir -p /home/vscode/.gradle /home/vscode/.android
