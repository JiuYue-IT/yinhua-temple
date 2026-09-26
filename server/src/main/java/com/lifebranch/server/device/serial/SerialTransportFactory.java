package com.lifebranch.server.device.serial;

import java.io.IOException;

@FunctionalInterface
public interface SerialTransportFactory {

    SerialTransport open(String portName) throws IOException;
}
