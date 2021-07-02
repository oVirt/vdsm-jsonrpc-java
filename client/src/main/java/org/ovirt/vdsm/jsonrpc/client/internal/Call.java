package org.ovirt.vdsm.jsonrpc.client.internal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.ovirt.vdsm.jsonrpc.client.BrokerCommandCallback;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcRequest;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcResponse;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * <code>Call</code> holds single response
 *
 */
public class Call implements Future<JsonRpcResponse>, JsonRpcCall {

    private final CountDownLatch latch;
    private final JsonNode id;
    private JsonRpcResponse response;
    private BrokerCommandCallback callback;

    public Call(JsonRpcRequest req) {
        this.latch = new CountDownLatch(1);
        this.id = req.getId();
    }

    public Call(JsonRpcRequest req, BrokerCommandCallback callback) {
        this(req);
        this.callback = callback;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public void addResponse(JsonRpcResponse response) {
        if (this.response != null) {
            return;
        }
        this.response = response;
        latch.countDown();
    }

    public JsonNode getId() {
        return this.id;
    }

    @Override
    public JsonRpcResponse get() throws InterruptedException {
        latch.await();
        return this.response;
    }

    @Override
    public JsonRpcResponse get(long timeout, TimeUnit unit)
            throws InterruptedException,
            TimeoutException {
        if (!latch.await(timeout, unit)) {
            throw new TimeoutException();
        }
        return this.response;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return this.latch.getCount() == 0;
    }

    @Override
    public BrokerCommandCallback getCallback() {
        return this.callback;
    }
}
