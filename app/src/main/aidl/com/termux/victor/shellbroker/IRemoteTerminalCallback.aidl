package com.termux.victor.shellbroker;

/** Events for ONE remote terminal session, delivered by the transport provider (rustdeskV) to VictorSuite's terminal view. */
oneway interface IRemoteTerminalCallback {
    /** state: connecting | open | closed | failed. */
    void onState(String sessionId, String state, String reason);
    void onOutput(String sessionId, in byte[] data);
    void onExit(String sessionId, int status);
}
