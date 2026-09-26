package com.lifebranch.server.device.serial;

/** 串口的最小抽象，便于用假设备测试。实现需线程安全地支持「一个线程读、一个线程写」。 */
public interface SerialTransport {

    /** 读取可用字节；超时返回 0，端口错误或已关闭返回 -1。 */
    int read(byte[] buffer);

    /** 写出全部字节；返回写出的字节数，失败返回 -1。 */
    int write(byte[] data);

    void close();
}
