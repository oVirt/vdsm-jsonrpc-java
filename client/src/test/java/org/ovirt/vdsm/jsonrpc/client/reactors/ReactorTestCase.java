package org.ovirt.vdsm.jsonrpc.client.reactors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompCommonClient.DEFAULT_REQUEST_QUEUE;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompCommonClient.DEFAULT_RESPONSE_QUEUE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.ovirt.vdsm.jsonrpc.client.ClientConnectionException;
import org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompClientPolicy;
import org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompReactor;
import org.ovirt.vdsm.jsonrpc.testutils.FreePorts;
import org.ovirt.vdsm.jsonrpc.testutils.TimeDepending;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReactorTestCase {
    private static final Logger log = LoggerFactory.getLogger(ReactorTestCase.class);
    private static final int TIMEOUT_SEC = 6;
    private static final String HOSTNAME = "127.0.0.1";
    private static final String DATA = "Hello World!";
    private Reactor reactorForListener;
    private Reactor reactorForClient;

    @Before
    public void setUp() throws Exception {
        this.reactorForListener = new StompReactor();
        this.reactorForClient = new StompReactor();
    }

    @After
    public void tearDown() {
        this.reactorForListener.close();
        this.reactorForClient.close();
    }

    @Test
    public void testConnectionBetweenListenerAndClient() throws InterruptedException,
            ExecutionException, TimeoutException,
            ClientConnectionException {
        final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(1);
        int port = FreePorts.findFreePort();
        final Future<ReactorListener> futureListener = this.reactorForListener.createListener(HOSTNAME,
                port,
                client -> client.addEventListener(message -> {
                    try {
                        client.sendMessage(message);
                    } catch (ClientConnectionException e) {
                        fail();
                    }
                }));

        ReactorListener listener = futureListener.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertNotNull(listener);
        assertTrue(futureListener.isDone());

        ReactorClient client = this.reactorForClient.createClient(HOSTNAME, port);
        var policy = new StompClientPolicy(180000,
                0,
                10000,
                IOException.class,
                DEFAULT_REQUEST_QUEUE,
                DEFAULT_RESPONSE_QUEUE);
        client.setClientPolicy(policy);
        assertNotNull(client);

        client.addEventListener(queue::add);

        final ByteBuffer buff = ByteBuffer.allocate(DATA.length());
        buff.put(DATA.getBytes());
        buff.position(0);
        client.connect();
        client.sendMessage(buff.array());
        byte[] message = queue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertNotNull(message);
        assertArrayEquals(buff.array(), message);

        final Future<Void> clientCloseTask = client.close();
        closeWithTimeout(clientCloseTask);
        assertTrue(clientCloseTask.isDone());
        final Future<Void> listenerCloseTask = listener.close();
        closeWithTimeout(listenerCloseTask);
        assertTrue(listenerCloseTask.isDone());
    }

    @Test
    @Category(TimeDepending.class)
    public void testNotConnectedRetry() throws InterruptedException, TimeoutException, ClientConnectionException,
            ExecutionException {
        final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(1);
        int port = FreePorts.findFreePort();
        Future<ReactorListener> futureListener = this.reactorForListener.createListener(HOSTNAME,
                port,
                client -> client.addEventListener(message -> {
                    try {
                        client.sendMessage(message);
                    } catch (ClientConnectionException e) {
                        fail();
                    }
                }));

        ReactorListener listener = futureListener.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertNotNull(listener);
        assertTrue(futureListener.isDone());

        ReactorClient client = this.reactorForClient.createClient(HOSTNAME, port);
        var policy =
                new StompClientPolicy(200, 10, 0, IOException.class, DEFAULT_REQUEST_QUEUE, DEFAULT_RESPONSE_QUEUE);
        client.setClientPolicy(policy);
        assertNotNull(client);

        client.addEventListener(queue::add);
        client.connect();
        final Future<Void> closeTask = listener.close();
        closeWithTimeout(closeTask);
        assertTrue(closeTask.isDone());

        futureListener = this.reactorForListener.createListener(HOSTNAME,
                //fixme this port is occasionally not free. Most likely it is because of combination of
                // 1. its state in TIME_WAIT where it enters after listener.close()
                // 2. and delayed TCP packets(fragments) more details at [1]
                // [1] https://vincent.bernat.ch/en/blog/2014-tcp-time-wait-state-linux
                port,
                _client -> _client.addEventListener(message -> {
                    try {
                        _client.sendMessage(message);
                    } catch (ClientConnectionException e) {
                        fail();
                    }
                }));

        listener = futureListener.get(TIMEOUT_SEC, TimeUnit.SECONDS);

        final ByteBuffer buff = ByteBuffer.allocate(DATA.length());
        buff.put(DATA.getBytes());
        buff.position(0);

        client.sendMessage(buff.array());
        byte[] message = queue.poll(TIMEOUT_SEC, TimeUnit.SECONDS);

        assertNotNull(message);
        assertArrayEquals(buff.array(), message);
        closeWithTimeout(listener.close());
    }

    private static void closeWithTimeout(Future<Void> closeTask) throws ExecutionException, InterruptedException, TimeoutException {
        closeTask.get(2, TimeUnit.SECONDS);
    }
}
