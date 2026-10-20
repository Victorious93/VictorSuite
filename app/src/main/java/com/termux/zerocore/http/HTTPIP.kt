package com.termux.zerocore.http

object HTTPIP {
    // Manifests are served from this repo's own `remote/` directory instead of a
    // third-party server, so their content is version-controlled and auditable.
    public const val IP = "https://raw.githubusercontent.com/Victorious93/VictorSuite/main/remote"
    //还得脚本位置
    public const val QEMU_HAI = "$IP/"
    // Community / support: the owner-controlled GitHub issue tracker.
    public const val ZERO_BBS = "https://github.com/Victorious93/VictorSuite/issues"
    // Update check: GitHub Releases "latest" on the owner repo. While the repo is
    // private this returns 404 unauthenticated; ZTGitHubVersion treats any
    // non-200/parse failure as "no update info" and keeps the badge hidden.
    public const val GITHUB_VERSION = "https://api.github.com/repos/Victorious93/VictorSuite/releases/latest"

}
