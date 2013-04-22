#!/system/bin/sh
mount -o remount,rw -t rootfs rootfs /
mount -t vfat -o ro,fmask=0000,dmask=0000,shortname=lower /dev/block/mmcblk0p31 /firmware/mdm
mount -t vfat -o ro,fmask=0000,dmask=0000,shortname=lower /dev/block/mmcblk0p16 /firmware/q6
mount -o remount,ro -t rootfs rootfs /
start kickstart
