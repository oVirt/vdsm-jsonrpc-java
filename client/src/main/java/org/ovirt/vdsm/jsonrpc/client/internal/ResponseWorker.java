package org.ovirt.vdsm.jsonrpc.client.internal;

import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.UTF8;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.logException;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.mapValues;

import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.ovirt.vdsm.jsonrpc.client.JsonRpcClient;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcEvent;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcResponse;
import org.ovirt.vdsm.jsonrpc.client.events.EventPublisher;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorClient;
import org.ovirt.vdsm.jsonrpc.client.reactors.ReactorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * <code>ResponseWorker</code> is responsible to process responses for all the {@link JsonRpcClient} and it is produced
 * by {@link ReactorFactory}.
 *
 */
public final class ResponseWorker extends Thread {
    private final LinkedBlockingQueue<MessageContext> queue;
    private static final ObjectMapper MAPPER;
    private final ResponseTracker tracker;
    private final EventPublisher publisher;
    private static final Logger log = LoggerFactory.getLogger(ResponseWorker.class);
    static {
        MAPPER = new ObjectMapper(new JsonFactoryBuilder()
                .configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false)
                .configure(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES, false)
                .build());
    }

    public ResponseWorker(int parallelism, int eventTimeoutInHours) {
        this.queue = new LinkedBlockingQueue<>();
        this.tracker = new ResponseTracker();
        this.publisher =
                new EventPublisher(new ForkJoinPool(parallelism,
                        ResponseForkJoinWorkerThread::new,
                        null,
                        true),
                        eventTimeoutInHours);

        Thread trackerThread = new Thread(this.tracker);
        trackerThread.setName("Response tracker");
        trackerThread.setDaemon(true);
        trackerThread.start();

        setName("ResponseWorker");
        setDaemon(true);
        start();
    }

    static class ResponseForkJoinWorkerThread extends ForkJoinWorkerThread {

        protected ResponseForkJoinWorkerThread(ForkJoinPool pool) {
            super(pool);
        }
    }

    /**
     * Registers new client with <code>ResponseWorker</code>.
     *
     * @param client
     *            - {@link JsonRpcClient} to be registered.
     * @return Client wrapper.
     */
    public JsonRpcClient register(ReactorClient client) {
        final JsonRpcClient jsonRpcClient = new JsonRpcClient(client, this.tracker);
        client.addEventListener(message -> queue.add(new MessageContext(jsonRpcClient, message)));
        return jsonRpcClient;
    }

    public void run() {
        AtomicReference<MessageContext> contextRef = new AtomicReference<>();
        while (true) {
            try {
                contextRef.set(this.queue.take());
                if (contextRef.get().getClient() == null) {
                    break;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Message received: " + new String(contextRef.get().getMessage(), UTF8));
                }
                JsonNode rootNode = MAPPER.readTree(contextRef.get().getMessage());
                if (!rootNode.isArray()) {
                    processIncomingObject(contextRef.get().getClient(), rootNode);
                } else {
                    rootNode.elements()
                            .forEachRemaining(node -> processIncomingObject(contextRef.get().getClient(), node));
                }
            } catch (Exception e) {
                log.warn("Exception thrown during message processing");
                if (log.isDebugEnabled()) {
                    log.debug(e.getMessage(), e);
                }
            }
        }
    }

    private void processIncomingObject(JsonRpcClient client, JsonNode node) {
        final JsonNode id = node.get("id");
        final JsonNode error = node.get("error");
        if (error != null && !NullNode.class.isInstance(error)) {
            JsonRpcResponse response = JsonRpcResponse.fromJsonNode(node);
            Map<String, Object> map = mapValues(response.getError());
            Object code = map.get("code");
            if (String.class.isInstance(code)) {
                String hostId = (String) code;
                if (hostId.contains(":")) {
                    String host = hostId.substring(0, hostId.indexOf(":"));
                    ObjectNode params = MAPPER.createObjectNode();
                    params.put(JsonRpcEvent.ERROR_KEY, (String) map.get("message"));

                    JsonRpcEvent event = new JsonRpcEvent(host + "|*|*|*", params);
                    processNotifications(event);
                }
            }
            client.processResponse(response);
            return;
        }

        if (id == null || NullNode.class.isInstance(id)) {
            JsonRpcEvent event = JsonRpcEvent.fromJsonNode(node);
            String method = client.getHostname() + event.getMethod();
            event.setMethod(method);
            if (log.isDebugEnabled()) {
                log.debug("Event arrived from " + client.getHostname() + " containing " + event.getParams());
            }
            processNotifications(event);
            return;
        }
        try {
            client.processResponse(JsonRpcResponse.fromJsonNode(node));
        } catch (IllegalArgumentException e) {
            logException(log, "Received response is not correct", e);
        }
    }

    private void processNotifications(JsonRpcEvent notification) {
        this.publisher.process(notification);
    }

    public void close() {
        this.queue.add(new MessageContext(null, null));
        this.tracker.close();
        this.publisher.close();
    }

    /**
     * @return publisher which can be used to subscribe to events defined by subscription id.
     */
    public EventPublisher getPublisher() {
        return this.publisher;
    }

}
