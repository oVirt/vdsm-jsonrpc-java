package org.ovirt.vdsm.jsonrpc.client.reactors.stomp;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.SSLStompClientTestCase.createProvider;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompCommonClient.DEFAULT_REQUEST_QUEUE;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.StompCommonClient.DEFAULT_RESPONSE_QUEUE;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;

import org.junit.experimental.categories.Category;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.ovirt.vdsm.jsonrpc.client.ClientConnectionException;
import org.ovirt.vdsm.jsonrpc.client.TestManagerProvider;
import org.ovirt.vdsm.jsonrpc.client.reactors.Reactor;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorClient;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorListener;
import org.ovirt.vdsm.jsonrpc.testutils.TimeDepending;

// Takes a long time to finish
@RunWith(Theories.class)
public class HeartbeatTestCase {

    private static final String HOSTNAME = "localhost";
    private static final int WAIT_TIMEOUT = 10;

    @DataPoint
    public static int heartbeat_1 = 3000;

    @DataPoint
    public static int heartbeat_2 = 0;

    @Theory
    @Category(TimeDepending.class)
    public void testSSLHeartbeat(int incoming, int outgoing) {
        TestManagerProvider provider = null;
        Reactor listeningReactor = null;
        Reactor sendingReactor = null;
        try {
            provider = createProvider();
            SSLContext context = provider.getSSLContext();
            listeningReactor = new SSLStompReactor(context);
            sendingReactor = new SSLStompReactor(context);

            testHeartbeat(listeningReactor, sendingReactor, incoming, outgoing);
        } catch (GeneralSecurityException | IOException | ClientConnectionException | InterruptedException
                | ExecutionException | TimeoutException e ) {
            fail();
        } finally {
            if (provider != null) {
                provider.closeStreams();
                provider = null;
            }
            if (sendingReactor != null) {
                sendingReactor.close();
            }
            if (listeningReactor != null) {
                listeningReactor.close();
            }
        }
    }

    @Theory
    @Category(TimeDepending.class)
    public void testPlainHeartbeat(int incoming, int outgoing) {
        Reactor listeningReactor = null;
        Reactor sendingReactor = null;

        try {
            listeningReactor = new StompReactor();
            sendingReactor = new StompReactor();

            this.testHeartbeat(listeningReactor, sendingReactor, incoming, outgoing);
        } catch (IOException | ClientConnectionException | InterruptedException | ExecutionException
                | TimeoutException e) {
            fail();
        } finally {
            if (sendingReactor != null) {
                sendingReactor.close();
            }
            if (listeningReactor != null) {
                listeningReactor.close();
            }
        }
    }

    private ReactorClient listeningClient = null;

    private void testHeartbeat(Reactor listeningReactor, Reactor sendingReactor, int incoming, int outgoing)
            throws ClientConnectionException,
            InterruptedException, ExecutionException, TimeoutException {
        Future<ReactorListener> futureListener =
                listeningReactor.createListener(HOSTNAME, 0, client -> listeningClient = client);

        ReactorListener listener = futureListener.get(30,TimeUnit.SECONDS);
        assertNotNull(listener);

        ReactorClient client = sendingReactor.createClient(HOSTNAME, listener.getPort());
        var policy = new StompClientPolicy(180000, 0, incoming, DEFAULT_REQUEST_QUEUE, DEFAULT_RESPONSE_QUEUE);
        policy.setOutgoingHeartbeat(outgoing);
        client.setClientPolicy(policy);
        client.connect();

        TimeUnit.SECONDS.sleep(WAIT_TIMEOUT);

        assertTrue(client.isOpen());
        assertTrue(this.listeningClient.isOpen());

        client.close().get(2, TimeUnit.SECONDS);
        listener.close().get(2, TimeUnit.SECONDS);
        this.listeningClient = null;
    }

}
