## Specify phone tech before including full_phone
$(call inherit-product, vendor/cm/config/gsm.mk)

# Release name
PRODUCT_RELEASE_NAME := m7

# Inherit some common CM stuff.
$(call inherit-product, vendor/cm/config/common_full_phone.mk)

# Inherit device configuration
$(call inherit-product, device/htc/m7/device_m7.mk)

## Device identifier. This must come after all inclusions
PRODUCT_DEVICE := m7
PRODUCT_NAME := cm_m7
PRODUCT_BRAND := htc
PRODUCT_MODEL := M7
PRODUCT_MANUFACTURER := HTC
