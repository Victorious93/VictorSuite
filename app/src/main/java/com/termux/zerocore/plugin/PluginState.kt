package com.termux.zerocore.plugin

/**
 * A registered plugin is always [DISABLED] until the user both acknowledges authorized
 * use ([PluginRegistry.acknowledgeAuthorizedUse]) and explicitly enables it
 * ([PluginRegistry.enable]). There is no code path that creates a plugin already
 * [ENABLED].
 */
enum class PluginState {
    DISABLED,
    ENABLED
}
