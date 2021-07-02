package org.ovirt.vdsm.jsonrpc.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompCommonClient.DEFAULT_REQUEST_QUEUE;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompCommonClient.DEFAULT_RESPONSE_QUEUE;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ovirt.vdsm.jsonrpc.client.internal.ClientPolicy;
import org.ovirt.vdsm.jsonrpc.client.internal.ResponseWorker;
import org.ovirt.vdsm.jsonrpc.client.reactors.Reactor;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorClient;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorListener;
import org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompClientPolicy;
import org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompReactor;
import org.ovirt.vdsm.jsonrpc.testutils.FreePorts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonRpcClientConnectivityTestCase {

    private static final String HOSTNAME = "127.0.0.1";

    private static final int CONNECTION_RETRY = 1;
    private static final int TIMEOUT = 1000;
    private static final int TIMEOUT_SEC = 3;
    private static final int HEART_BEAT = 1000;
    private static final int EVENT_TIMEOUT_IN_HOURS = 10;

    private int port;
    private Reactor clientReactor;
    private Reactor listenerReactor;
    private ResponseWorker worker;

    @Before
    public void setup() throws IOException {
        port = FreePorts.findFreePort();
        worker = new ResponseWorker(Runtime.getRuntime().availableProcessors(), EVENT_TIMEOUT_IN_HOURS);
        this.clientReactor = new StompReactor();
        this.listenerReactor = new StompReactor();
    }

    @After
    public void tearDown() {
        this.clientReactor.close();
        this.listenerReactor.close();
        this.worker.close();
    }

    @Test(expected = ConnectException.class)
    public void testDelayedConnect() throws Throwable {
        // Given
        final ReactorClient client = clientReactor.createClient(HOSTNAME, port);
        StompClientPolicy policy = new StompClientPolicy(TIMEOUT,
                CONNECTION_RETRY,
                HEART_BEAT,
                IOException.class,
                DEFAULT_REQUEST_QUEUE,
                DEFAULT_RESPONSE_QUEUE);
        client.setClientPolicy(policy);
        JsonRpcClient jsonClient = worker.register(client);
        jsonClient.setRetryPolicy(new ClientPolicy(TIMEOUT, 0, 0));

        assertNotNull(jsonClient);
        assertFalse(client.isOpen());

        // When
        try {
            // When
            final JsonNode params = jsonFromString("{\"text\": \"running test: testDelayedConnect\"}");
            final JsonNode id = jsonFromString("457");
            final JsonRpcRequest req = new JsonRpcRequest("echo", params, id);

            jsonClient.call(req);

            // Then
            fail();
        } catch (ClientConnectionException e) {
            throw e.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRetryMessageSend() throws InterruptedException, ExecutionException, TimeoutException,
            ClientConnectionException {
        // Given
        Future<ReactorListener> futureListener = listenerReactor.createListener(HOSTNAME,
                port,
                client -> client.addEventListener(message -> {
                    // if timing is wrong ignore the message
                }));

        ReactorListener listener = futureListener.get(TIMEOUT_SEC, TimeUnit.SECONDS);

        final ReactorClient client = clientReactor.createClient(HOSTNAME, port);
        client.setClientPolicy(new StompClientPolicy(TIMEOUT,
                CONNECTION_RETRY,
                HEART_BEAT,
                IOException.class,
                DEFAULT_REQUEST_QUEUE,
                DEFAULT_RESPONSE_QUEUE));
        JsonRpcClient jsonClient = worker.register(client);
        jsonClient.setRetryPolicy(new ClientPolicy(TIMEOUT, 2, HEART_BEAT));
        assertNotNull(jsonClient);
        assertFalse(client.isOpen());

        // When
        final JsonNode params = jsonFromString("{\"text\": \"running test: testRetryMessageSend\"}");
        final JsonNode id = jsonFromString("345");
        final JsonRpcRequest req = new JsonRpcRequest("echo", params, id);

        Future<JsonRpcResponse> future = jsonClient.call(req);
        listener.close();
        clientReactor.close();
        listenerReactor.close();
        client.close();

        // Then
        JsonRpcResponse response = future.get(5, TimeUnit.SECONDS);
        assertNotNull(response);

        ResponseDecomposer decomposer = new ResponseDecomposer(response);
        Map<String, Object> error = decomposer.decomposeError();
        assertNotNull(error);
        Map<String, Object> status = (Map<String, Object>) error.get("status");
        assertEquals(5022, status.get("code"));
    }

    private static JsonNode jsonFromString(String str) {
        final JsonFactory jsonFactory = new ObjectMapper().getFactory();
        try (JsonParser jp = jsonFactory.createParser(str)) {
            return jp.readValueAsTree();
        } catch (Exception e) {
            return null;
        }
    }
}
