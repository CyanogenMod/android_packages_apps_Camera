#!/system/bin/sh
# Copyright (c) 2010-2012, Code Aurora Forum. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#     * Redistributions of source code must retain the above copyright
#       notice, this list of conditions and the following disclaimer.
#     * Redistributions in binary form must reproduce the above
#       copyright notice, this list of conditions and the following
#       disclaimer in the documentation and/or other materials provided
#       with the distribution.
#     * Neither the name of Code Aurora Forum, Inc. nor the names of its
#       contributors may be used to endorse or promote products derived
#      from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
# WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
# ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
# BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
# BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
# OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
# IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#
# This script will load and unload the wifi driver to put the wifi in
# in deep sleep mode so that there won't be voltage leakage.
# Loading/Unloading the driver only incase if the Wifi GUI is not going
# to Turn ON the Wifi. In the Script if the wlan driver status is
# ok(GUI loaded the driver) or loading(GUI is loading the driver) then
# the script won't do anything. Otherwise (GUI is not going to Turn On
# the Wifi) the script will load/unload the driver
# This script will get called after post bootup.

for i in 1 2 3 4 5 6 7 8 9 10; do
  target=`getprop ro.product.wireless`
  case "$target" in
    WCN3660*)
        # The property below is used in Qcom SDK for softap to determine
        # the wifi driver config file
        setprop wlan.driver.config /system/etc/firmware/wlan/prima/WCNSS_qcom_cfg.ini
        # We need to make sure the WCNSS platform driver is running.
        # The WCNSS platform driver can either be built as a loadable
        # module or it can be built-in to the kernel.  If it is built
        # as a loadable module it can have one of several names.  So
        # look to see if an appropriately named kernel module is
        # present
        wcnssmod=`ls /system/lib/modules/wcnss*.ko`
        case "$wcnssmod" in
            *wcnss*)
                # A kernel module is present, so load it
                insmod $wcnssmod
                ;;
            *)
                # A kernel module is not present so we assume the
                # driver is built-in to the kernel.  If that is the
                # case then the driver will export a file which we
                # must touch so that the driver knows that userspace
                # is ready to handle firmware download requests.  See
                # if an appropriately named device file is present
                wcnssnode=`ls /dev/wcnss*`
                case "$wcnssnode" in
                    *wcnss*)
                        # There is a device file.  Write to the file
                        # so that the driver knows userspace is
                        # available for firmware download requests
                        echo 1 > $wcnssnode
                        ;;
                    *)
                        # There is not a kernel module present and
                        # there is not a device file present, so
                        # the driver must not be available
                        echo "No WCNSS module or device node detected"
                        ;;
                esac
                ;;
        esac
        # Plumb down the device serial number
        serialno=`getprop ro.serialno`
        echo $serialno > /sys/devices/platform/wcnss_wlan.0/serial_number
        ;;
    msm8660*)
    exit 0
    ;;
    msm7627a*)
        wlanchip=`cat /persist/wlan_chip_id`
        echo "The WLAN Chip ID is $wlanchip"
        case "$wlanchip" in
            "AR6003")
             rm  /system/lib/modules/wlan.ko
             ln -s /system/wifi/ar6000.ko /system/lib/modules/wlan.ko
             mv /system/bin/wpa_supplicant /system/bin/wpa_supplicant_wcn
             ln -s /system/others/wpa_supplicant /system/bin/wpa_supplicant
             ;;
            *)
             echo "********************************************************************"
              echo "*** Error:WI-FI chip ID is not specified in /persist/wlan_chip_id **"
             echo "*******    WI-FI may not work    ***********************************"
             ;;
        esac
    ;;
    msm7630*)
        wifishd=`getprop wlan.driver.status`
        wlanchip=`cat /persist/wlan_chip_id`
        echo "The WLAN Chip ID is $wlanchip"
        case "$wlanchip" in
            "WCN1314")
             ln -s /system/lib/modules/volans/WCN1314_rf.ko /system/lib/modules/wlan.ko
             ;;
            "WCN1312")
             ln -s /system/lib/modules/libra/libra.ko /system/lib/modules/wlan.ko
	      ln -s /data/hostapd/qcom_cfg.ini /etc/firmware/wlan/qcom_cfg.ini
             ln -s /persist/qcom_wlan_nv.bin /etc/firmware/wlan/qcom_wlan_nv.bin
	      ;;
           *)
            echo "********************************************************************"
	     echo "*** Error:WI-FI chip ID is not specified in /persist/wlan_chip_id **"
            echo "*******    WI-FI may not work    ***********************************"
            ;;
        esac
        case "$wifishd" in
            "ok")
             ;;
            "loading")
            ;;
           *)
               case "$wlanchip" in
                   "WCN1314")
                    ;;

                   "WCN1312")
                        /system/bin/amploader -i
                        ;;
                   *)
	                ;;
               esac
        esac
    ;;
    msm7627*)
        ln -s /data/hostapd/qcom_cfg.ini /etc/firmware/wlan/qcom_cfg.ini
        ln -s /persist/qcom_wlan_nv.bin /etc/firmware/wlan/qcom_wlan_nv.bin
        wifishd=`getprop wlan.driver.status`
        case "$wifishd" in
            "ok")
             ;;
            "loading")
             ;;
            *)
# For the new .38 kernel for 1312, there was an FFA panic
# when no 1312/1314 chip was present. Hence this is commented out
# Will need to reenable this code for 1312.
#
#                /system/bin/amploader -i
            ;;
        esac
    ;;

    *)
      ;;
  esac
  sleep 1
  log -p i -t "QCOMWIFI" "No Target Info"
done
exit 0
