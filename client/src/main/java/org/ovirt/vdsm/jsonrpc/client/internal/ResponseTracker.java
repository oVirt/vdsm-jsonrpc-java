package org.ovirt.vdsm.jsonrpc.client.internal;

import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.buildErrorResponse;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.buildFailedResponse;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.getTimeout;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.jsonToByteArray;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.mapValues;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ovirt.vdsm.jsonrpc.client.ClientConnectionException;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcRequest;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcResponse;
import org.ovirt.vdsm.jsonrpc.client.RequestAlreadySentException;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorClient;
import org.ovirt.vdsm.jsonrpc.client.utils.LockWrapper;
import org.ovirt.vdsm.jsonrpc.client.utils.ResponseTracking;
import org.ovirt.vdsm.jsonrpc.client.utils.retry.RetryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * Response tracker thread is responsible for tracking and retrying requests. For each connection there is single
 * instance of the thread.
 *
 */
public class ResponseTracker implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ResponseTracker.class);
    private static final int TRACKING_TIMEOUT = 500;
    private final AtomicBoolean isTracking;
    private final ConcurrentMap<JsonNode, JsonRpcCall> runningCalls;
    private final ConcurrentMap<JsonNode, ResponseTracking> map;
    private final ConcurrentMap<String, List<JsonNode>> hostToId;
    private final Queue<JsonNode> queue;
    private final Lock lock;
    private ScheduledExecutorService executorService;

    public ResponseTracker() {
        this.isTracking = new AtomicBoolean(true);
        this.runningCalls = new ConcurrentHashMap<>();
        this.map = new ConcurrentHashMap<>();
        this.hostToId = new ConcurrentHashMap<>();
        this.queue = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();
    }

    private void removeRequestFromTracking(JsonNode id) {
        try (LockWrapper ignored = new LockWrapper(this.lock)) {
            this.queue.remove(id);
            ResponseTracking tracking = this.map.remove(id);
            if (tracking != null && tracking.getClient() != null) {
                this.hostToId.computeIfPresent(tracking.getClient().getClientId(), (clientId, nodes) -> {
                    nodes.remove(id);
                    return nodes;
                });
            }
        }
    }

    public void registerCall(JsonRpcRequest req, JsonRpcCall call) {
        if (this.runningCalls.putIfAbsent(req.getId(), call) != null) {
            throw new RequestAlreadySentException();
        }
    }

    public JsonRpcCall removeCall(JsonNode id) {
        removeRequestFromTracking(id);
        return this.runningCalls.remove(id);
    }

    public void registerTrackingRequest(JsonRpcRequest req, ResponseTracking tracking) {
        JsonNode id = req.getId();
        List<JsonNode> nodes = new CopyOnWriteArrayList<>();
        try (LockWrapper ignored = new LockWrapper(this.lock)) {
            this.map.put(id, tracking);

            if(!this.queue.contains(id)){
                this.queue.add(id);
            }

            nodes.add(id);
            nodes = this.hostToId.putIfAbsent(tracking.getClient().getClientId(), nodes);
            if (nodes != null && !nodes.contains(id)) {
                nodes.add(id);
            }
        }
    }

    @Override
    public void run() {
        try {
            while (this.isTracking.get()) {
                TimeUnit.MILLISECONDS.sleep(TRACKING_TIMEOUT);
                loop();
            }
        } catch (InterruptedException e) {
            log.warn("Tracker thread interrupted");
        }
    }

    protected void loop() {
        for (JsonNode id : queue) {
            if (this.runningCalls.computeIfAbsent(id, _id -> {
                removeRequestFromTracking(id);
                return null;
            }) == null) {
                continue;
            }

            ResponseTracking tracking = this.map.get(id);
            if (System.currentTimeMillis() >= tracking.getTimeout()) {
                RetryContext context = tracking.getContext();
                context.decreaseAttempts();
                if (context.getNumberOfAttempts() <= 0) {
                    handleFailure(tracking, id, "Too many attempts");
                    continue;
                }
                final byte[] message = jsonToByteArray(tracking.getRequest().toJson());
                if (log.isDebugEnabled()){
                    log.debug("Message to be sent {}", new String(message, StandardCharsets.UTF_8));
                }
                tracking.getClient().sendMessage(message);
                tracking.setTimeout(getTimeout(context.getTimeout(), context.getTimeUnit()));
            } else {
                log.debug("Tracking timeout detected for request id {} ", id.asText());
            }
        }
    }

    public void close() {
        this.isTracking.set(false);
    }

    private void handleFailure(ResponseTracking tracking, JsonNode id, String failureDetails) {
        log.debug("Failure for request id {}. Details: {}", id.asText(), failureDetails);
        remove(tracking, id, buildFailedResponse(tracking.getRequest()));
        if (tracking.isResetConnection() && !tracking.getClient().isOpen()) {
            tracking.getClient().disconnect("Vds timeout occurred");
        }
    }

    public void setExecutorService(ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    private void remove(ResponseTracking tracking, JsonNode id, JsonRpcResponse response) {
        try (LockWrapper ignored = new LockWrapper(this.lock)) {
            JsonRpcCall call = this.runningCalls.remove(id);
            boolean callbackNotified = false;
            if (call != null) {
                call.addResponse(response);
                if (call.getCallback() != null && executorService != null) {
                    callbackNotified = true;
                    executorService.schedule(() -> call.getCallback().onFailure(mapValues(response.getError())),
                            0,
                            TimeUnit.SECONDS);
                }
            }
            removeRequestFromTracking(id);
            if (!callbackNotified && tracking != null && tracking.getClient() != null) {
                tracking.getCall().addResponse(response);
                if (tracking.getCall().getCallback() != null && executorService != null) {
                    executorService.schedule(() -> tracking.getCall()
                            .getCallback()
                            .onFailure(mapValues(response.getError())),
                            0,
                            TimeUnit.SECONDS);
                }
            }
        }
    }

    public void processIssue(JsonRpcResponse response) {
        JsonNode error = response.getError();
        Map<String, Object> map = mapValues(error);
        String code = (String) map.get("code");
        String message = (String) map.get("message");
        JsonRpcResponse errorResponse = buildErrorResponse(null, 5022, message);

        try (LockWrapper ignored = new LockWrapper(this.lock)) {
            if (ReactorClient.CLIENT_CLOSED.equals(message)) {
                removeNodes(this.hostToId.get(code), errorResponse);
            } else {
                String hostname = code.substring(0, code.indexOf(":"));
                this.hostToId.keySet().stream().filter(key -> key.startsWith(hostname))
                        .forEach(key -> removeNodes(this.hostToId.get(key), errorResponse));
            }
        }
    }

    private void removeNodes(List<JsonNode> nodes, JsonRpcResponse errorResponse) {
        nodes.stream()
                .filter(id -> !(id instanceof NullNode))
                .forEach(id -> remove(this.map.get(id), id, errorResponse));
    }

    protected Map<String, List<JsonNode>> getHostMap() {
        return this.hostToId;
    }
}
