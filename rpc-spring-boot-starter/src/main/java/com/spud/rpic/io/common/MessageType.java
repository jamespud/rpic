package com.spud.rpic.io.common;

import lombok.Getter;

/**
 * @author Spud
 * @date 2025/2/27
 */
@Getter
public enum MessageType {
    REQUEST((byte) 1),
    RESPONSE((byte) 2),
    HEARTBEAT((byte) 3),
    HEARTBEAT_RESPONSE((byte) 4);

    private final byte type;

    MessageType(byte type) {
        this.type = type;
    }

    public static MessageType valueOf(byte type) {
        for (MessageType messageType : values()) {
            if (messageType.type == type) {
                return messageType;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + type);
    }
}