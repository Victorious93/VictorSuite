# Third-party notices

VictorSuite is free software released under the **GNU General Public License v3** (see [LICENSE.md](LICENSE.md)).
It is a **modified version** (GPLv3 §5a) of the projects below. All original copyright notices in source files are kept
unchanged; this file adds credits, it does not replace any of them.

## Upstream projects

| Project | Authors | Licence | Source |
|---|---|---|---|
| Termux app (terminal, bootstrap, `RUN_COMMAND`) | Fredrik Fornwall and the Termux developers | GPLv3 (some parts Apache-2.0, see below) | https://github.com/termux/termux-app |
| Termux:API (`termux-api-lib`) | Termux developers | GPLv3 | https://github.com/termux/termux-api |
| ZeroTermux (`com.termux.zerocore` code, now `com.termux.victor`; drawer tools, management features) | hanxinhao000 | GPLv3 only | https://github.com/hanxinhao000/ZeroTermux |
| Terminal Emulator for Android (`terminal-view`, `terminal-emulator`) | Jack Palevich | Apache-2.0 | https://github.com/jackpal/Android-Terminal-Emulator |
| libcore/ojluni (`com.termux.shared.file`) | The Android Open Source Project / Oracle | GPLv2 with Classpath exception | https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/ |
| libsuperuser (`StreamGobbler`) | Chainfire | Apache-2.0 | https://github.com/Chainfire/libsuperuser |

Application icons in the historical ZeroTermux project are credited to the Alibaba Iconfont library.

## Bundled binaries in `app/libs`

These files are committed as prebuilt binaries. The **Status** column says whether the licence text and the matching
source code are known. GPLv3 §6 requires that we can offer the corresponding source for every GPL binary we ship.

| File | Package | Licence | Source / notes | Status |
|---|---|---|---|---|
| `avnc.aar` | `com.gaurav.avnc` | GPL-3.0 (bundles Apache-2.0, BSD libjpeg-turbo, X11, sshlib licence texts in `assets/license/`) | https://github.com/gujjwal00/avnc | OK – licence texts inside the aar |
| `termux-x11.aar` | `com.termux.x11` | GPL-3.0 | https://github.com/termux/termux-x11 | No licence text inside the aar; source public |
| `stub.aar` | `com.termux.x11.stub` | GPL-3.0 (assumed, part of Termux:X11 build) | https://github.com/termux/termux-x11 | ⚠️ **No licence text, exact source revision unknown** |
| `xh_lib.aar` | `com.example.xh_lib` | Unknown (ZeroTermux author's helper library, assumed GPLv3 with ZeroTermux) | Not published as far as we know | ⚠️ **No source and no licence text** |
| `z_file.aar` | `com.zp.z_file` (contains `com.zp.z_file.zerotermux.*`) | Unknown. Appears to be a modified ZFileManager; the modifications are ZeroTermux-specific | Upstream ZFileManager source; modified source not in this repo | ⚠️ **No source for the modified build and no licence text** |
| `FHttpServer.aar` | `cn.hotapk.fhttpserver` | Unknown (hotapk FHttpServer) | https://github.com/hotapk/FHttpServer | ⚠️ **No licence text inside the aar – verify** |
| `qrcodecore.aar` | `cn.bingoogolapple.qrcode.core` | Apache-2.0 | https://github.com/bingoogolapple/BGAQRCode-Android | No licence text inside the aar |
| `zxing.aar` | `cn.bingoogolapple.qrcode.zxing` | Apache-2.0 (wraps ZXing, Apache-2.0) | https://github.com/bingoogolapple/BGAQRCode-Android | No licence text inside the aar |
| `jlan-5.jar` | `org.alfresco.jlan` | GPL (Alfresco JLAN; exact version – GPLv2/GPLv3/LGPL – **verify**) | Alfresco JLAN 5 source | ⚠️ **No licence text inside the jar** |
| `log4j-1.2.17.jar` | `org.apache.log4j` | Apache-2.0 | https://logging.apache.org/log4j/1.x/ | Licence in jar. ⚠️ **End of life, known CVEs (e.g. CVE-2019-17571, CVE-2021-4104, CVE-2022-23302/23305/23307) – replace (plan batch 11)** |
| `slf4j-api-1.7.21.jar` | `org.slf4j` | MIT | https://www.slf4j.org/ | No licence text inside the jar |
| `slf4j-log4j12-1.7.21.jar` | `org.slf4j.impl` | MIT | https://www.slf4j.org/ | No licence text inside the jar |
| `ftpserver-core-1.1.1-edited.jar` | `org.apache.ftpserver` | Apache-2.0 (locally edited build) | https://mina.apache.org/ftpserver-project/ | Licence + NOTICE in jar; edits not documented |
| `ftplet-api-1.1.1.jar` | `org.apache.ftpserver.ftplet` | Apache-2.0 | https://mina.apache.org/ftpserver-project/ | Licence + NOTICE in jar |
| `mina-core-2.0.16.jar` | `org.apache.mina` | Apache-2.0 | https://mina.apache.org/ | Licence + NOTICE in jar |
| `commons-codec-1.10-rep.jar` | `org.apache.commons.codec` (repackaged) | Apache-2.0 | https://commons.apache.org/proper/commons-codec/ | Licence + NOTICE in jar |
| `commons-logging-1.2.jar` | `org.apache.commons.logging` | Apache-2.0 | https://commons.apache.org/proper/commons-logging/ | Licence + NOTICE in jar |
| `commons-net-3.8.0.jar` | `org.apache.commons.net` | Apache-2.0 | https://commons.apache.org/proper/commons-net/ | Licence + NOTICE in jar |

### Open compliance items

1. `xh_lib.aar` and `z_file.aar` ship without source or licence text (`stub.aar` is now built from source). Audit and options: [docs/LICENSE_AUDIT.md](docs/LICENSE_AUDIT.md). Either locate the exact source
   (hanxinhao000 repositories / Termux:X11) and link it here, or replace these libraries.
2. `FHttpServer.aar` and `jlan-5.jar` licences must be confirmed against upstream.
3. `log4j-1.2.17.jar` is end-of-life with published CVEs and should be removed or replaced.

## Libraries resolved from Maven / JitPack

Declared in `app/build.gradle`; licences as published by each project.

| Library | Licence |
|---|---|
| AndroidX (annotation, core, drawerlayout, preference, viewpager, swiperefreshlayout, lifecycle, security-crypto), Material Components | Apache-2.0 |
| Markwon | Apache-2.0 |
| Guava, Gson | Apache-2.0 |
| Kotlin coroutines | Apache-2.0 |
| libsu (topjohnwu) | Apache-2.0 |
| RootBeer | Apache-2.0 |
| libaums, libaums-httpserver, libaums-storageprovider | Apache-2.0 |
| usbdeviceenumerator | Apache-2.0 |
| AgentWeb | Apache-2.0 |
| XXPermissions | Apache-2.0 |
| OkGo, OkHttp | Apache-2.0 |
| Aria (me.laoyuyu.aria) | Apache-2.0 |
| SmartSwipe | Apache-2.0 |
| Glide | BSD / MIT / Apache-2.0 (see Glide LICENSE) |
| ImmersionBar | Apache-2.0 |
| ZXing core | Apache-2.0 |
| NanoHTTPD | BSD-3-Clause |
| jsoup | MIT |
| commons-fileupload | Apache-2.0 |
| dd-plist | MIT |
| adblib (tananaev) | BSD-3-Clause |
| jCIFS | LGPL-2.1 |
| un7zip (hzy) | Apache-2.0 / LGPL (wraps p7zip) – verify |
| fastandrutils (hotapk), switchbutton, numberprogressbar, colorseekbar, imagepicker, plugin-locale, RealtimeBlurView_v2 | Apache-2.0 (as published) – verify per release |
| Material Symbols (Google, `ic_ms_*.xml` vector drawables) | Apache-2.0 – https://github.com/google/material-design-icons |

If you are a copyright holder and something here is wrong or missing, please open an issue at
https://github.com/Victorious93/VictorSuite/issues.
