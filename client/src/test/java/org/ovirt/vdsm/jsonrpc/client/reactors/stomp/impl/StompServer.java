package org.ovirt.vdsm.jsonrpc.client.reactors.stomp.impl;

import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.impl.Message.HEADER_ACK;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.impl.Message.HEADER_DESTINATION;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.impl.Message.HEADER_ID;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.impl.Message.HEADER_MESSAGE;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.impl.Message.HEADER_RECEIPT;
import static org.ovirt.vdsm.jsonrpc.client.reactors.stomp.impl.Message.HEADER_TRANSACTION;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.ovirt.vdsm.jsonrpc.client.reactors.stomp.impl.Message.Command;
import org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils;
import org.ovirt.vdsm.jsonrpc.client.utils.LockWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StompServer implements Reciever {
    public static final Logger log = LoggerFactory.getLogger(StompServer.class);
    private final StompTransport transport;
    private final Map<String, List<TestServerListener>> listeners;
    private final Map<String, String> destinations;
    private final Map<String, List<Message>> transactions;
    private final Lock lock;
    @SuppressWarnings("serial")
    private final Map<String, TestCommandExecutor> commands;

    public StompServer(String host) throws IOException {
        this.listeners = new ConcurrentHashMap<>();
        this.destinations = new ConcurrentHashMap<>();
        this.transactions = new ConcurrentHashMap<>();
        this.lock = new ReentrantLock();
        this.commands = setupCommandsHandlers();
        this.transport = new StompTransport(host, this);
        this.transport.listen();
    }

    public void stop() throws IOException {
        this.transport.close();
        this.listeners.clear();
        this.destinations.clear();
    }

    @Override
    public void recieve(Message message, SelectionKey key) {
        String command = message.getCommand();
        TestCommandExecutor executor = this.commands.get(command);
        Message response = executor.execute(message, key);
        if (response != null) {
            this.transport.send(response.build(), key);
        }
        if (Command.DISCONNECT.toString().equals(command)) {
            try {
                key.channel().close();
            } catch (IOException ignored) {
            }
        }
    }

    public int getPort() {
        return this.transport.getPort();
    }

    private Map<String, TestCommandExecutor> setupCommandsHandlers() {
        return new HashMap<>() {
            {
                put(Command.CONNECT.toString(),
                        (message, key) -> new Message().connected()
                                .withHeader("session", UUID.randomUUID().toString()));
                put(Command.SUBSCRIBE.toString(), new TestCommandExecutor() {

                    @Override
                    public Message execute(Message message, SelectionKey key) {
                        Map<String, String> headers = message.getHeaders();
                        String destHeader = headers.get(HEADER_DESTINATION);
                        String idHeader = headers.get(HEADER_ID);

                        if (JsonUtils.isEmpty(destHeader) || JsonUtils.isEmpty(idHeader)) {
                            return new Message().error().withHeader(HEADER_MESSAGE, "Missing required header");
                        }
                        try (LockWrapper ignored = new LockWrapper(lock)) {
                            List<TestServerListener> list = listeners.get(destHeader);
                            if (list == null) {
                                list = new CopyOnWriteArrayList<>();
                                listeners.put(destHeader, list);
                            }
                            list.add(new TestServerListener(key, transport));
                            destinations.put(idHeader, destHeader);
                            if (!JsonUtils.isEmpty(headers.get(HEADER_ACK))) {
                                return new Message().ack().withHeader(HEADER_ID, idHeader);
                            }
                        }
                        return null;
                    }
                });
                put(Command.SEND.toString(), (message, key) -> {
                    Map<String, String> headers = message.getHeaders();
                    String destHeader = headers.get(HEADER_DESTINATION);

                    if (JsonUtils.isEmpty(destHeader)) {
                        return new Message().error().withHeader(HEADER_MESSAGE, "Missing required header");
                    }
                    try (LockWrapper ignored = new LockWrapper(lock)) {
                        String transactionHeader = headers.get(HEADER_TRANSACTION);
                        if (!JsonUtils.isEmpty(transactionHeader)) {
                            List<Message> messages = transactions.get(transactionHeader);
                            if (messages != null) {
                                messages.add(message);
                            }
                        } else {
                            List<TestServerListener> list = listeners.get(destHeader);
                            if (list != null && !list.isEmpty()) {
                                for (TestServerListener serverListener : list) {
                                    serverListener.update(new Message().message()
                                            .withContent(message.getContent())
                                            .withHeaders(message.getHeaders()));
                                }
                            }
                        }
                    }
                    return null;
                });
                put(Command.UNSUBSCRIBE.toString(), (message, key) -> {
                    Map<String, String> headers = message.getHeaders();
                    String idHeader = headers.get(HEADER_ID);

                    if (JsonUtils.isEmpty(idHeader)) {
                        return new Message().error().withHeader(HEADER_MESSAGE, "Missing required header");
                    }
                    try (LockWrapper ignored = new LockWrapper(lock)) {
                        String destination = destinations.remove(idHeader);
                        if (destination == null) {
                            return new Message().error().withHeader(HEADER_MESSAGE, "Not recognized subscribtion id");
                        }
                        listeners.remove(destination);
                    }
                    return null;
                });
                put(Command.DISCONNECT.toString(), (message, key) -> {
                    Map<String, String> headers = message.getHeaders();
                    String receipt = headers.get(HEADER_RECEIPT);

                    if (JsonUtils.isEmpty(receipt)) {
                        return new Message().error().withHeader(HEADER_MESSAGE, "Missing required header");
                    }
                    return new Message().receipt().withHeaders(headers);
                });
                put(Command.BEGIN.toString(), (message, key) -> {
                    Map<String, String> headers = message.getHeaders();
                    String transactionHeader = headers.get(HEADER_TRANSACTION);

                    if (JsonUtils.isEmpty(transactionHeader)) {
                        return new Message().error().withHeader(HEADER_MESSAGE, "Missing required header");
                    }
                    try (LockWrapper ignored = new LockWrapper(lock)) {
                        List<Message> messages = transactions.get(transactionHeader);
                        if (messages != null) {
                            return new Message().error()
                                    .withHeader(HEADER_MESSAGE,
                                            "Transaction with this id already exists");
                        }
                        transactions.put(transactionHeader, new ArrayList<>());
                    }
                    return null;
                });
                put(Command.ABORT.toString(), (message, key) -> {
                    Map<String, String> headers = message.getHeaders();
                    String transactionHeader = headers.get(HEADER_TRANSACTION);

                    if (JsonUtils.isEmpty(transactionHeader)) {
                        return new Message().error().withHeader(HEADER_MESSAGE, "Missing required header");
                    }

                    try (LockWrapper ignored = new LockWrapper(lock)) {
                        transactions.remove(transactionHeader);
                    }
                    return null;
                });
                put(Command.COMMIT.toString(), (message, key) -> {
                    Map<String, String> headers = message.getHeaders();
                    String transactionHeader = headers.get(HEADER_TRANSACTION);

                    if (JsonUtils.isEmpty(transactionHeader)) {
                        return new Message().error().withHeader(HEADER_MESSAGE, "Missing required header");
                    }
                    try (LockWrapper ignored = new LockWrapper(lock)) {
                        List<Message> messages = transactions.remove(transactionHeader);
                        if (messages == null) {
                            return new Message().error().withHeader(HEADER_MESSAGE, "No transaction with provided id");
                        }
                        for (Message msg : messages) {
                            String destHeader = msg.getHeaders().get(HEADER_DESTINATION);
                            List<TestServerListener> list = listeners.get(destHeader);
                            if (list == null) {
                                log.error("Listeners not found for {}. All registered listeners {}",
                                        destHeader,
                                        listeners);
                            } else {
                                for (TestServerListener listener : list) {
                                    listener.update(new Message().message()
                                            .withContent(msg.getContent())
                                            .withHeaders(msg.getHeaders()));
                                }
                            }
                        }
                    }
                    return null;
                });
            }
        };
    }
}
