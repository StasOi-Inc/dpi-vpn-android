#!/usr/bin/env bash
set -euo pipefail

echo "=== Building Rust proxy library for Android (cdylib) ==="
echo "=== Architectures: arm64-v8a (SDK 24), armeabi-v7a (SDK 21) ==="

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SDK_PATH="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK_PATH" ] && [ -f "$ROOT_DIR/local.properties" ]; then
    SDK_PATH="$(grep '^sdk\.dir=' "$ROOT_DIR/local.properties" | head -n1 | cut -d= -f2- | sed 's|\\\\|/|g; s|\\:|:|g')"
fi
if [ -z "$SDK_PATH" ]; then
    echo "Error: Android SDK not found."
    echo "  Set ANDROID_SDK_ROOT/ANDROID_HOME, or add sdk.dir to local.properties"
    exit 1
fi
echo "Using SDK: $SDK_PATH"

case "$(uname -s)-$(uname -m)" in
    Darwin-arm64) HOST_TAGS="darwin-arm64 darwin-x86_64" ;;
    Darwin*)      HOST_TAGS="darwin-x86_64 darwin-arm64" ;;
    *)            HOST_TAGS="linux-x86_64" ;;
esac
NDK_BIN=""
for HOST_TAG in $HOST_TAGS; do
    NDK_BIN=$( (ls -d "$SDK_PATH"/ndk/*/toolchains/llvm/prebuilt/"$HOST_TAG"/bin 2>/dev/null | sort -V | tail -n1) || true )
    if [ -n "$NDK_BIN" ]; then
        break
    fi
done
if [ -z "$NDK_BIN" ]; then
    echo "Error: no NDK toolchain found in $SDK_PATH/ndk"
    exit 1
fi
echo "Using NDK bin: $NDK_BIN"

for t in aarch64-linux-android armv7-linux-androideabi; do
    rustup target add --toolchain stable "$t"
done

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_BIN/aarch64-linux-android24-clang"
export CC_aarch64_linux_android="$NDK_BIN/aarch64-linux-android24-clang"
export AR_aarch64_linux_android="$NDK_BIN/llvm-ar"

export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$NDK_BIN/armv7a-linux-androideabi21-clang"
export CC_armv7_linux_androideabi="$NDK_BIN/armv7a-linux-androideabi21-clang"
export AR_armv7_linux_androideabi="$NDK_BIN/llvm-ar"

export RUSTFLAGS="--remap-path-prefix=$HOME/.cargo=CARGO_HOME"

mkdir -p "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a" "$ROOT_DIR/app/src/main/jniLibs/armeabi-v7a"
cd "$ROOT_DIR"

echo
echo "[1/2] arm64-v8a (release, API 24)..."
cargo build --release --target aarch64-linux-android
cp "target/aarch64-linux-android/release/libtgwsproxy.so" "app/src/main/jniLibs/arm64-v8a/libtgwsproxy.so"

echo
echo "[2/2] armeabi-v7a (release, API 21)..."
cargo build --release --target armv7-linux-androideabi
cp "target/armv7-linux-androideabi/release/libtgwsproxy.so" "app/src/main/jniLibs/armeabi-v7a/libtgwsproxy.so"

echo
ls -la app/src/main/jniLibs/arm64-v8a/libtgwsproxy.so app/src/main/jniLibs/armeabi-v7a/libtgwsproxy.so
echo "=== BUILD SUCCESS ==="
