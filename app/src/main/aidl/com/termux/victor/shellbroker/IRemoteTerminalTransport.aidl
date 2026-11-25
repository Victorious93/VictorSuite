package com.termux.victor.shellbroker;

import android.os.Bundle;
import com.termux.victor.shellbroker.IRemoteTerminalCallback;

/**
 * Transport provider contract, API version 1. Implemented by rustdeskV (`VictorTerminalTransportService`): it owns the
 * authenticated connection to the exact peer; VictorSuite owns the terminal UI. Methods returning String return "" on success or
 * a stable error code ("not_trusted", "no_such_session", "unavailable", ...). The provider must authenticate THIS caller
 * (Binder.getCallingUid → package `com.termux` → pinned certificate) before serving anything.
 */
interface IRemoteTerminalTransport {
    int apiVersion();
    /** {peers: ArrayList<Bundle{peer_id, name, platform, terminal_ok}>}. */
    Bundle listPeers();
    /** Connects a terminal to exactly [peerId]. Returns "session:<id>" on success, else "error:<code>:<reason>". */
    String openPeer(String peerId, int cols, int rows);
    /** Starts delivering output/exit for a session this caller owns (or was handed). */
    String attach(String sessionId, IRemoteTerminalCallback callback);
    String input(String sessionId, in byte[] data);
    String resize(String sessionId, int cols, int rows);
    String close(String sessionId);
}
