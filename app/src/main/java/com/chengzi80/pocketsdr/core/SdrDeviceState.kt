package com.chengzi80.pocketsdr.core

enum class SdrDeviceState {
    DISCONNECTED,
    DETECTING,
    PERMISSION_REQUIRED,
    CONNECTING,
    CONNECTED,
    ERROR
}
