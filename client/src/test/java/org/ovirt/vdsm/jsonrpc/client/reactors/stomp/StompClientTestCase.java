package org.ovirt.vdsm.jsonrpc.client.reactors.stomp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.SSLStompClientTestCase.generateRandomMessage;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompCommonClient.DEFAULT_REQUEST_QUEUE;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompCommonClient.DEFAULT_RESPONSE_QUEUE;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.UTF8;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ovirt.vdsm.jsonrpc.client.ClientConnectionException;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorClient;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorListener;

public class StompClientTestCase {
    private static final int TIMEOUT_SEC = 20;
    private static final String HOSTNAME = "localhost";
    private StompReactor listeningReactor;
    private StompReactor sendingReactor;

    @Before
    public void setUp() throws IOException {
        this.listeningReactor = new StompReactor();
        this.sendingReactor = new StompReactor();
    }

    @After
    public void tearDown() {
        this.sendingReactor.close();
        this.listeningReactor.close();
    }

    @Test
    public void testHelloWorld() throws InterruptedException, ExecutionException, ClientConnectionException {
        testEchoMessage(generateRandomMessage(16));
    }

    @Test
    public void testLongMessage() throws InterruptedException, ExecutionException, ClientConnectionException {
        testEchoMessage(generateRandomMessage(524288));
    }

    private void testEchoMessage(String message) throws ClientConnectionException, InterruptedException,
            ExecutionException {
        final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(1);
        Future<ReactorListener> futureListener =
                this.listeningReactor.createListener(HOSTNAME,
                        0,
                        client -> client.addEventListener(client::sendMessage)
                );

        ReactorListener listener = futureListener.get();
        assertNotNull(listener);

        ReactorClient client = this.sendingReactor.createClient(HOSTNAME, listener.getPort());
        client.setClientPolicy(
                new StompClientPolicy(180000, 0, 1000000, DEFAULT_REQUEST_QUEUE, DEFAULT_RESPONSE_QUEUE));
        client.addEventListener(queue::add);
        client.connect();

        client.sendMessage(message.getBytes(UTF8));
        byte[] response = queue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);

        client.close();
        listener.close();

        assertNotNull(response);
        assertEquals(message, new String(response, UTF8));
    }
}
