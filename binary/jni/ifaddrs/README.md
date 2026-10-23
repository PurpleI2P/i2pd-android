# ifaddrs

`getifaddrs()` and `freeifaddrs()` for Android 6.0 (API 23): bionic has them only since API 24, and
i2pd uses them for interface MTU, the `ifname` options and IPv6/Yggdrasil address detection
(`libi2pd/util.cpp` includes `"ifaddrs.h"` on Android).

The code is bionic's implementation (AOSP `libc/bionic/ifaddrs.cpp` and `bionic_netlink.cpp`), formerly
the `PurpleI2P/android-ifaddrs` submodule. As in bionic, apps do not send `RTM_GETLINK` (Android 11+
forbids it): names and flags of interfaces come from `if_indextoname` and `SIOCGIFFLAGS`.

Licenses: see `LICENSE` (BSD 2-clause, `bionic_macros.h` under Apache 2.0).
