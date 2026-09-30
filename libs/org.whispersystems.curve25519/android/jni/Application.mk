# Modern ABI set (armeabi and mips are unsupported by current Android and NDK);
# AGP passes APP_ABI/APP_PLATFORM explicitly, this file documents the manual ndk-build configuration
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_PLATFORM := android-21
