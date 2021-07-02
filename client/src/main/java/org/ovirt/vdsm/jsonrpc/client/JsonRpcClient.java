package org.ovirt.vdsm.jsonrpc.client;

import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.getTimeout;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.jsonToByteArray;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.mapValues;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.ovirt.vdsm.jsonrpc.client.internal.Call;
import org.ovirt.vdsm.jsonrpc.client.internal.ClientPolicy;
import org.ovirt.vdsm.jsonrpc.client.internal.JsonRpcCall;
import org.ovirt.vdsm.jsonrpc.client.internal.ResponseTracker;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorClient;
import org.ovirt.vdsm.jsonrpc.client.utils.JsonResponseUtil;
import org.ovirt.vdsm.jsonrpc.client.utils.ResponseTracking;
import org.ovirt.vdsm.jsonrpc.client.utils.retry.RetryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ReactorClient} wrapper which provides ability to send single or batched requests.
 *
 * Each send operation is represented by {@link Call} future which is updated when response arrives.
 *
 */
public class JsonRpcClient {
    private final Logger log = LoggerFactory.getLogger(JsonRpcClient.class);
    private final ReactorClient client;
    private ResponseTracker tracker;
    private ClientPolicy policy;
    private ScheduledExecutorService executorService;

    /**
     * Wraps {@link ReactorClient} to hide response update details.
     *
     * @param client - used to communicate.
     * @param tracker - used for response tracking.
     */
    public JsonRpcClient(ReactorClient client, ResponseTracker tracker) {
        this.client = client;
        this.tracker = tracker;
    }

    public void setClientRetryPolicy(ClientPolicy policy) {
        this.client.setClientPolicy(policy);
    }

    public void setRetryPolicy(ClientPolicy policy) {
        this.policy = policy;
    }

    public ClientPolicy getClientRetryPolicy() {
        return this.client.getRetryPolicy();
    }

    public ClientPolicy getRetryPolicy() {
        return this.policy;
    }

    public String getHostname() {
        return this.client.getHostname();
    }

    public int getConnectionId() {
        return this.client.getConnectionId();
    }

    public void setExecutorService(ScheduledExecutorService executorService) {
        this.executorService = executorService;
        this.tracker.setExecutorService(executorService);
    }

    /**
     * Sends single request and returns {@link Future} representation of {@link JsonRpcResponse}.
     *
     * @param req - Request which is about to be sent.
     * @return Future representation of the response or <code>null</code> if sending failed.
     * @throws ClientConnectionException is thrown when connection issues occur.
     * @throws RequestAlreadySentException when the same requests is attempted to be send twice.
     */
    public Future<JsonRpcResponse> call(JsonRpcRequest req) throws ClientConnectionException {
        final Call call = new Call(req);
        this.tracker.registerCall(req, call);
        retryCall(req, call);
        try {
            this.getClient().sendMessage(jsonToByteArray(req.toJson()));
        } finally {
            retryCall(req, call);
        }
        return call;
    }

    public Future<JsonRpcResponse> call(JsonRpcRequest req, BrokerCommandCallback callback)
            throws ClientConnectionException {
        final Call call = new Call(req, callback);
        this.tracker.registerCall(req, call);
        retryCall(req, call);
        boolean exceptionOccurred = false;
        try {
            this.getClient().sendMessage(jsonToByteArray(req.toJson()));
        } catch (ClientConnectionException ex) {
            exceptionOccurred = true;
            throw ex;
        } finally {
            if (exceptionOccurred) {
                removeCall(call);
            } else {
                retryCall(req, call);
            }
        }
        return call;
    }

    public void removeCall(Future<JsonRpcResponse> call) {
        if (!Call.class.isInstance(call)) {
            return;
        }
        this.tracker.removeCall(((Call)call).getId());
    }

    private void retryCall(final JsonRpcRequest request, final JsonRpcCall call) throws ClientConnectionException {
        ResponseTracking tracking =
                new ResponseTracking(request, call, new RetryContext(policy), getTimeout(this.policy.getRetryTimeOut(),
                        this.policy.getTimeUnit()), this.client, !Objects.equals(request.getMethod(), "Host.ping"));
        this.tracker.registerTrackingRequest(request, tracking);
    }

    public ReactorClient getClient() throws ClientConnectionException {
        if (this.client.isOpen()) {
            return this.client;
        }
        this.client.connect();
        return this.client;
    }

    public void processResponse(JsonRpcResponse response) {
        JsonNode id = response.getId();
        if (NullNode.class.isInstance(id) || id == null) {
            this.tracker.processIssue(response);
            return;
        }
        JsonRpcCall call = this.tracker.removeCall(response.getId());
        if (call == null) {
            this.log.warn("Not able to update response for {}", response);
            return;
        }
        call.addResponse(response);
        if (call.getCallback() != null && executorService != null) {
            if (response.getError() != null) {
                executorService.schedule(() -> call.getCallback().onFailure(mapValues(response.getError())),
                        0,
                        TimeUnit.SECONDS);
            } else {
                executorService.schedule(() -> call.getCallback().onResponse(new JsonResponseUtil().populate(response)),
                        0,
                        TimeUnit.SECONDS);
            }
        }
    }

    public void close() {
        this.client.close();
    }

    public boolean isClosed() {
        return client.isOpen();
    }
}
