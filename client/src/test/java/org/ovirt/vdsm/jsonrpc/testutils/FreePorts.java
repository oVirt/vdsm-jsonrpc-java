package org.ovirt.vdsm.jsonrpc.testutils;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Utility to select random free tcp port on localhost. Solution based on: https://gist.github.com/vorburger/3429822
 */
public class FreePorts {

    public static int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException ignored) {
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
        throw new IllegalStateException("Could not find a free TCP/IP port");
    }
}
