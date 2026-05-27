LOCAL_PATH := $(call my-dir)

FIRMWARE_IMAGES := $(wildcard $(LOCAL_PATH)/prebuilts/firmware/*.img)

$(foreach f,$(notdir $(FIRMWARE_IMAGES)), \
    $(call add-radio-file,prebuilts/firmware/$(f)))

AB_OTA_PARTITIONS += \
    $(foreach f,$(notdir $(FIRMWARE_IMAGES)),$(basename $(f)))
