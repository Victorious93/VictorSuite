package com.termux.shared.termux;

/**
 * User-facing VictorSuite URLs that termux-shared can open or show.
 * Keep these values identical to {@code com.termux.victor.config.VsLinks}
 * for the same names. The app's VsLinks re-exports these constants.
 */
public final class VictorSuiteLinks {

    public static final String REPO = "https://github.com/Victorious93/VictorSuite";
    public static final String RELEASES = REPO + "/releases";
    public static final String ISSUES = REPO + "/issues";

    /** GitHub Pages site built from docs/. Verified live. */
    public static final String DOCS_SITE = "https://victorious93.github.io/VictorSuite/";

    /** In-app help / manual on the Pages site. */
    public static final String HELP = DOCS_SITE + "MANUAL.html";

    private VictorSuiteLinks() {}
}
