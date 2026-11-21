package com.termux.victor.shellbroker;

import com.termux.victor.shellbroker.IShellSessionCallback;

/**
 * VictorSuite shell broker, API version 1. Callers are identified by Binder.getCallingUid() (OS-attested) and must be on the owner's
 * trust list with a pinned signing certificate. Methods returning String return "" on success, otherwise a stable reject code
 * (see RejectCode). There is deliberately no fallback to RUN_COMMAND: if this service is missing, untrusted or outdated, callers
 * must show that reason and stop.
 */
interface IShellBroker {
    int apiVersion();
    /** {api_version:int, enabled:boolean, root_allowed:boolean, root_available:boolean, implementation:String} */
    Bundle capabilities();
    /** Request keys: session_id, peer, target, mode, privilege, command?, cols, rows, nonce, expires_ms, binding?, caller_api_version. */
    String open(in Bundle request, IShellSessionCallback callback);
    String input(String sessionId, in byte[] data);
    String resize(String sessionId, int cols, int rows);
    String signal(String sessionId, String name);
    String close(String sessionId, String reason);
}
