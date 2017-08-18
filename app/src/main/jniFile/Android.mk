LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := decodeHandler
LOCAL_SRC_FILES := DecodeHandlerJni.c

include $(BUILD_SHARED_LIBRARY)