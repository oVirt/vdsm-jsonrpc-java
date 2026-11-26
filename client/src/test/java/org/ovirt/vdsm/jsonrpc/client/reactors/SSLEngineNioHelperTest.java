package org.ovirt.vdsm.jsonrpc.client.reactors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

import org.junit.Test;

public class SSLEngineNioHelperTest {

    private SSLEngine stalledEngine(SSLSession session) throws Exception {
        SSLEngine engine = mock(SSLEngine.class);
        when(engine.getSession()).thenReturn(session);
        when(engine.wrap(any(ByteBuffer.class), any(ByteBuffer.class)))
                .thenReturn(new SSLEngineResult(
                        SSLEngineResult.Status.OK,
                        SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                        0, 0));
        return engine;
    }

    private SSLSession session() {
        SSLSession session = mock(SSLSession.class);
        when(session.getApplicationBufferSize()).thenReturn(1024);
        when(session.getPacketBufferSize()).thenReturn(2048);
        return session;
    }

    @Test(timeout = 30000)
    public void writeFailsWhenWrapMakesNoProgress() throws Exception {
        SocketChannel channel = mock(SocketChannel.class);
        when(channel.write(any(ByteBuffer.class))).thenReturn(0);
        SSLEngineNioHelper helper =
                new SSLEngineNioHelper(channel, stalledEngine(session()), null, null);

        ByteBuffer buff = ByteBuffer.allocate(128);
        buff.put(new byte[64]);
        buff.flip();

        long start = System.currentTimeMillis();
        try {
            helper.write(buff);
            fail("Expected IOException when SSLEngine.wrap() makes no progress");
        } catch (IOException expected) {
            // the reactor closes the connection instead of spinning forever
        }
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed >= 20000) {
            fail("write should fail fast, took " + elapsed + " ms");
        }
    }

    @Test(timeout = 30000)
    public void writeSucceedsWhenWrapMakesProgress() throws Exception {
        SocketChannel channel = mock(SocketChannel.class);
        when(channel.write(any(ByteBuffer.class))).thenAnswer(inv -> {
            ByteBuffer b = inv.getArgument(0);
            int n = b.remaining();
            b.position(b.position() + n);
            return n;
        });
        SSLEngine engine = mock(SSLEngine.class);
        SSLSession session = session();
        when(engine.getSession()).thenReturn(session);
        when(engine.wrap(any(ByteBuffer.class), any(ByteBuffer.class))).thenAnswer(inv -> {
            ByteBuffer src = inv.getArgument(0);
            ByteBuffer dst = inv.getArgument(1);
            int n = src.remaining();
            src.position(src.position() + n);
            dst.put(new byte[n]);
            return new SSLEngineResult(
                    SSLEngineResult.Status.OK,
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                    n, n);
        });
        SSLEngineNioHelper helper = new SSLEngineNioHelper(channel, engine, null, null);

        ByteBuffer buff = ByteBuffer.allocate(128);
        buff.put(new byte[64]);
        buff.flip();

        helper.write(buff);
        assertFalse(buff.hasRemaining());
    }
}
