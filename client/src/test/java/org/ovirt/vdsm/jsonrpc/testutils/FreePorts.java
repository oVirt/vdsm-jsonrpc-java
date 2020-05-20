package org.ovirt.vdsm.jsonrpc.testutils;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Utility to select random free tcp port on localhost. Solution based on: https://gist.github.com/vorburger/3429822
 */
public class FreePorts {

    public static synchronized int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            try {
                socket.close();
                Thread.sleep(500);
            } catch (IOException | InterruptedException ignored) {
            }
            return port;
        } catch (IOException ignored) {
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                    Thread.sleep(500);
                } catch (IOException | InterruptedException ignored) {
                }
            }
        }
        throw new IllegalStateException("Could not find a free TCP/IP port");
    }
}
