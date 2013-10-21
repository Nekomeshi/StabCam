LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

include C:/cygwin/home/masaki/OpenCV-2.4.6-android-sdk/sdk/native/jni/OpenCV.mk

LOCAL_MODULE    := stabilized_camera
LOCAL_SRC_FILES := stabilized_camera.cpp
LOCAL_LDLIBS +=  -llog -ldl
LOCAL_ARM_MODE := arm
TARGET_ARCH_ABI := armeabi-v7a

include $(BUILD_SHARED_LIBRARY)
