package com.lifebranch.server.device.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** jSerialComm 实现：115200 8N1，无流控，读 100ms 半阻塞超时。 */
public final class JSerialCommTransport implements SerialTransport {

    public static final int BAUD = 115200;

    private final SerialPort port;

    private JSerialCommTransport(SerialPort port) {
        this.port = port;
    }

    public static SerialTransport open(String portName) throws IOException {
        SerialPort p;
        try {
            p = SerialPort.getCommPort(portName);
        } catch (SerialPortInvalidPortException e) {
            throw new IOException("无效的串口名：" + portName, e);
        }
        p.setComPortParameters(BAUD, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        p.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        p.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING, 100, 1000);
        if (!p.openPort()) {
            throw new IOException("无法打开串口 " + portName + "（不存在，或被串口监视器等程序占用）");
        }
        return new JSerialCommTransport(p);
    }

    @Override
    public int read(byte[] buffer) {
        return port.readBytes(buffer, buffer.length);
    }

    @Override
    public int write(byte[] data) {
        return port.writeBytes(data, data.length);
    }

    @Override
    public void close() {
        port.closePort();
    }

    public record PortInfo(String name, String description) {
    }

    /** 枚举本机串口，现场用于确认 ESP32 对应的 COM 编号。 */
    public static List<PortInfo> listPorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(p -> new PortInfo(p.getSystemPortName(), p.getDescriptivePortName()))
                .toList();
    }
}
