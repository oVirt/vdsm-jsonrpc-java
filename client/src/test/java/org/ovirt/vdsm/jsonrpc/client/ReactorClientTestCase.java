package org.ovirt.vdsm.jsonrpc.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.ovirt.vdsm.jsonrpc.client.internal.ClientPolicy;
import org.ovirt.vdsm.jsonrpc.client.reactors.Reactor;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorClient;
import org.ovirt.vdsm.jsonrpc.client.utils.OneTimeCallback;

public class ReactorClientTestCase {
    private static class TestReactorClient extends ReactorClient {

        private boolean open;
        private boolean isInInit;

        public TestReactorClient(Reactor reactor, String hostname, int port) {
            super(reactor, hostname, port);

            this.open = true;
            this.isInInit = false;
            this.updateLastIncomingHeartbeat();
        }

        @Override
        protected void processIncoming() {
        }

        @Override
        public void sendMessage(byte[] message) {
        }

        @Override
        protected int read(ByteBuffer buff) {
            return 0;
        }

        @Override
        protected void write(ByteBuffer buff) {
        }

        @Override
        protected void postConnect(OneTimeCallback callback) {
        }

        @Override
        public void updateInterestedOps() {
        }

        @Override
        protected OneTimeCallback getPostConnectCallback() {
            return null;
        }

        @Override
        public void postDisconnect() {
        }

        @Override
        public boolean isInInit() {
            return this.isInInit;
        }

        public void setInInit(boolean isInInit) {
            this.isInInit = isInInit;
        }

        @Override
        protected byte[] buildNetworkResponse(String reason) {
            return null;
        }

        @Override
        protected void sendHeartbeat() {
        }

        @Override
        public void validate(ClientPolicy policy) {
        }

        @Override
        protected void clean() {
        }

        @Override
        protected void closeChannel() {
            this.open = false;
        }

        @Override
        public boolean isOpen() {
            return this.open;
        }

        public boolean isHalf() {
            return this.half;
        }
    }

    @Test
    public void testHeartbeat() throws IOException, ClientConnectionException {
        Reactor reactor = mock(Reactor.class);
        ReactorClient client = new TestReactorClient(reactor, "localhost", 0);
        ClientPolicy policy = new ClientPolicy(0, 0, 1000000, 1000000);
        client.setClientPolicy(policy);

        client.process();
        assertTrue(client.isOpen());
    }

    @Test
    public void testFailHeartbeat() throws IOException, ClientConnectionException {
        Reactor reactor = mock(Reactor.class);
        ReactorClient client = spy(new TestReactorClient(reactor, "localhost", 0));
        ClientPolicy policy = new ClientPolicy(0, 0, 500, 500);
        client.setClientPolicy(policy);

        long now = System.currentTimeMillis();
        when(client.now()).thenReturn(now, now + TimeUnit.SECONDS.toMillis(10));

        client.process();
        assertFalse(client.isOpen());
    }

    @Test
    public void testHalf() throws IOException, ClientConnectionException {
        Reactor reactor = mock(Reactor.class);
        TestReactorClient client = spy(new TestReactorClient(reactor, "localhost", 0));
        ClientPolicy policy = new ClientPolicy(0, 0, 500, 500);
        client.setClientPolicy(policy);

        long now = System.currentTimeMillis();
        when(client.now()).thenReturn(now + TimeUnit.SECONDS.toMillis(30), now);

        client.process();
        assertFalse(client.isHalf());
    }

    @Test
    public void testConnecting() throws IOException, ClientConnectionException {
        Reactor reactor = mock(Reactor.class);
        TestReactorClient client = spy(new TestReactorClient(reactor, "localhost", 0));
        ClientPolicy policy = new ClientPolicy(0, 0, 500, 500);
        client.setClientPolicy(policy);
        client.setInInit(true);

        long now = System.currentTimeMillis();
        when(client.now()).thenReturn(now, now + TimeUnit.SECONDS.toMillis(10));

        client.process();
        assertTrue(client.isOpen());
        assertTrue(client.isHalf());
    }
}