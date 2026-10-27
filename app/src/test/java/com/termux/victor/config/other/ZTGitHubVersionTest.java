package com.termux.victor.config.other;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ZTGitHubVersionTest {
    @Test
    public void newerTagShowsBadge() {
        assertTrue(ZTGitHubVersion.isNewerRelease("v0.118.3.56", "0.118.3.55"));
        assertTrue(ZTGitHubVersion.isNewerRelease("VictorSuite v0.119.0", "0.118.3.55"));
    }

    @Test
    public void sameOrOlderTagHidesBadge() {
        assertFalse(ZTGitHubVersion.isNewerRelease("v0.118.3.55", "0.118.3.55"));
        assertFalse(ZTGitHubVersion.isNewerRelease("v0.118.3", "0.118.3.55"));
        assertFalse(ZTGitHubVersion.isNewerRelease(null, "0.118.3.55"));
        assertFalse(ZTGitHubVersion.isNewerRelease("", "0.118.3.55"));
    }
}
