package com.termux.victor.config.other;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VsGitHubVersionTest {
    @Test
    public void newerTagShowsBadge() {
        assertTrue(VsGitHubVersion.isNewerRelease("v0.118.3.56", "0.118.3.55"));
        assertTrue(VsGitHubVersion.isNewerRelease("VictorSuite v0.119.0", "0.118.3.55"));
    }

    @Test
    public void sameOrOlderTagHidesBadge() {
        assertFalse(VsGitHubVersion.isNewerRelease("v0.118.3.55", "0.118.3.55"));
        assertFalse(VsGitHubVersion.isNewerRelease("v0.118.3", "0.118.3.55"));
        assertFalse(VsGitHubVersion.isNewerRelease(null, "0.118.3.55"));
        assertFalse(VsGitHubVersion.isNewerRelease("", "0.118.3.55"));
    }
}
