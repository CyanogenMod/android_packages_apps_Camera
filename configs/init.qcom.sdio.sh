#!/system/bin/sh
# Copyright (c) 2010, Code Aurora Forum. All rights reserved.
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

# For successful WLAN card detection, WLAN needs SDIO polling turned on.
# This script can be used to turn on/off SDIO polling on appropriate
# SDIO slot on the MSM target (e.g. slot 3 on 7x30 surf).

arg=$1
target=`getprop ro.product.device`

case "$target" in
    "msm7627_surf")
        echo "$arg" > /sys/devices/platform/msm_sdcc.1/polling
        echo "$arg" > /sys/devices/platform/msm_sdcc.2/polling
        ;;

    "msm7627_ffa")
        echo "$arg" > /sys/devices/platform/msm_sdcc.2/polling
        ;;

    "msm7627a")
        echo "$arg" > /sys/devices/platform/msm_sdcc.2/polling
        ;;

    "msm7630_surf")
        echo "$arg" > /sys/devices/platform/msm_sdcc.3/polling
        ;;

    "msm7630_1x")
        echo "$arg" > /sys/devices/platform/msm_sdcc.3/polling
        ;;

    "msm7630_fusion")
        echo "$arg" > /sys/devices/platform/msm_sdcc.3/polling
        ;;

    "msm8660")
        echo "$arg" > /sys/devices/platform/msm_sdcc.4/polling
        ;;

    "msm8660_csfb")
        echo "$arg" > /sys/devices/platform/msm_sdcc.4/polling
        ;;
esac

exit 0
