package org.ovirt.vdsm.jsonrpc.client.reactors.stomp.impl;

import java.nio.channels.SelectionKey;

public interface Receiver {
    void receive(Message message, SelectionKey key);
}
