package com.termux.victor.shellbroker;

/** Events for ONE session, delivered only to the app that opened it. Output is never logged or persisted by VictorSuite. */
oneway interface IShellSessionCallback {
    /** state: awaiting_approval | open | denied | closed | failed | revoked | expired. `binding` is set with `open`. */
    void onState(String sessionId, String state, String reason, String binding);
    /** channel: tty | stdout | stderr */
    void onOutput(String sessionId, in byte[] data, String channel);
    void onExit(String sessionId, int status);
}
