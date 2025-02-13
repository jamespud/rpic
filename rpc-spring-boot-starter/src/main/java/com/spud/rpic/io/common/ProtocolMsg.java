package com.spud.rpic.io.common;

/**
 * @author Spud
 * @date 2025/2/9
 */
public class ProtocolMsg {
    private byte magicNumber;
    private byte version;
    private byte type;
    private int contentLength;
    private byte[] content;

    // getters and setters
    public byte getMagicNumber() {
        return magicNumber;
    }

    public void setMagicNumber(byte magicNumber) {
        this.magicNumber = magicNumber;
    }

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public int getContentLength() {
        return contentLength;
    }

    public void setContentLength(int contentLength) {
        this.contentLength = contentLength;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
}