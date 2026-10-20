package com.termux.zerocore.http

object HTTPIP {
    // Manifests are served from this repo's own `remote/` directory instead of a
    // third-party server, so their content is version-controlled and auditable.
    public const val IP = "https://raw.githubusercontent.com/Victorious93/VictorSuite/main/remote"
    //还得脚本位置
    public const val QEMU_HAI = "$IP/"
    // Documentation for the Termux base VictorSuite is built on (opened by the "Termux Wiki" menu entry)
    public const val ZERO_BBS = "https://wiki.termux.com/"
    // GITHUB Version
    public const val GITHUB_VERSION = "https://api.github.com/repos/Victorious93/VictorSuite/releases/latest"

}
