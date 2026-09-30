LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libcurve25519-donna
LOCAL_SRC_FILES := curve25519-donna.c

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

# $(wildcard) resolves against ndk-build's working directory, which under AGP is the module
# root rather than jni/ - anchor every pattern to $(LOCAL_PATH), then re-relativize for
# LOCAL_SRC_FILES, which ndk-build resolves against $(LOCAL_PATH)
_ref10_c_files := $(wildcard $(LOCAL_PATH)/ed25519/*.c) \
                  $(wildcard $(LOCAL_PATH)/ed25519/additions/*.c) \
                  $(wildcard $(LOCAL_PATH)/ed25519/additions/generalized/*.c) \
                  $(wildcard $(LOCAL_PATH)/ed25519/nacl_sha512/*.c) \
                  $(LOCAL_PATH)/ed25519/tests/internal_fast_tests.c

LOCAL_MODULE     := libcurve25519-ref10
LOCAL_SRC_FILES  := $(_ref10_c_files:$(LOCAL_PATH)/%=%)

# include dirs anchored to $(LOCAL_PATH) so the module builds both via classic ndk-build and via AGP
LOCAL_C_INCLUDES := $(LOCAL_PATH)/ed25519/nacl_includes $(LOCAL_PATH)/ed25519/additions $(LOCAL_PATH)/ed25519/additions/generalized $(LOCAL_PATH)/ed25519/tests $(LOCAL_PATH)/ed25519/sha512 $(LOCAL_PATH)/ed25519

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE     := libcurve25519
LOCAL_SRC_FILES  := curve25519-jni.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/ed25519/additions $(LOCAL_PATH)/ed25519/additions/generalized $(LOCAL_PATH)/ed25519/tests

# 16 KB page-size compatibility (Android 15+ / Play requirement); does not change codegen of the library logic
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384

LOCAL_STATIC_LIBRARIES := libcurve25519-donna libcurve25519-ref10

include $(BUILD_SHARED_LIBRARY)

