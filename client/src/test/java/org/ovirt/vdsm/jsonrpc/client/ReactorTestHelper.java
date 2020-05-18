package org.ovirt.vdsm.jsonrpc.client;

import org.ovirt.vdsm.jsonrpc.client.reactors.Reactor;

public interface ReactorTestHelper {
    Reactor getReactor() throws Exception;

    String getUriScheme();
}
