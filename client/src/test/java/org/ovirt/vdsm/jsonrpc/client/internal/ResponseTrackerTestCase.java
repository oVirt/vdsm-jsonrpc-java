package org.ovirt.vdsm.jsonrpc.client.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.nio.channels.Selector;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Before;
import org.junit.Test;
import org.ovirt.vdsm.jsonrpc.client.ClientConnectionException;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcRequest;
import org.ovirt.vdsm.jsonrpc.client.reactors.Reactor;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorClient;
import org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompClient;
import org.ovirt.vdsm.jsonrpc.client.utils.ResponseTracking;

public class ResponseTrackerTestCase {

    private JsonRpcRequest request;

    private ResponseTracking tracking;

    private ReactorClient client;

    private JsonNode idNode = new TextNode(UUID.randomUUID().toString());

    @Before
    public void setup() throws ClientConnectionException {
        request = mock(JsonRpcRequest.class);
        tracking = mock(ResponseTracking.class);
        client = spy(new StompClient(mock(Reactor.class), mock(Selector.class), "127.0.0.1", 54321));
        when(request.getId()).thenReturn(idNode);
    }

    @Test
    public void testRegisterTrackingRequest() {
        when(client.getClientId()).thenReturn("127.0.0.1:" + client.hashCode());
        when(client.isOpen()).thenReturn(true);
        when(tracking.getClient()).thenReturn(client);

        ResponseTracker tracker = new ResponseTracker();
        tracker.registerTrackingRequest(request, tracking);

        assertEquals(1, tracker.getHostMap().size());
    }

    @Test
    public void testDoubleTrackingRequestRegistration() {
        when(client.getClientId()).thenReturn("127.0.0.1:" + client.hashCode());
        when(client.isOpen()).thenReturn(true);
        when(tracking.getClient()).thenReturn(client);

        ResponseTracker tracker = new ResponseTracker();
        tracker.registerTrackingRequest(request, tracking);
        tracker.registerTrackingRequest(request, tracking);

        assertEquals(1, tracker.getHostMap().size());
    }

    @Test
    public void testRegisterAndRemove() {
        when(client.getClientId()).thenReturn("127.0.0.1:" + client.hashCode());
        when(client.isOpen()).thenReturn(true);
        when(tracking.getClient()).thenReturn(client);

        ResponseTracker tracker = new ResponseTracker();
        tracker.registerTrackingRequest(request, tracking);
        tracker.removeCall(idNode);

        Map<String, List<JsonNode>> map = tracker.getHostMap();
        assertEquals(1, map.keySet().size());
        assertEquals(0, map.get(client.getClientId()).size());
    }
}
