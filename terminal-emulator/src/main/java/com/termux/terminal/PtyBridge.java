package com.termux.terminal;

// VictorSuite add {@
/**
 * Public entry to the package-private {@link JNI} pty calls, used only by the VictorSuite shell broker
 * (com.termux.victor.shellbroker). It adds no behaviour: the broker authenticates and gets the owner's approval before any call here.
 */
public final class PtyBridge {
    private PtyBridge() { }

    public static int createSubprocess(String cmd, String cwd, String[] args, String[] envVars, int[] processId, int rows, int columns) {
        return JNI.createSubprocess(cmd, cwd, args, envVars, processId, rows, columns, 0, 0);
    }

    public static void setPtyWindowSize(int fd, int rows, int cols) { JNI.setPtyWindowSize(fd, rows, cols, 0, 0); }

    public static int waitFor(int processId) { return JNI.waitFor(processId); }
}
// @}
