package org.ovirt.vdsm.jsonrpc.client.reactors.stomp.impl;

import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.UTF8;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.isEmpty;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ovirt.vdsm.jsonrpc.client.ClientConnectionException;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcRequest;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class Message {
    public enum Command {
        SEND,
        SUBSCRIBE,
        UNSUBSCRIBE,
        BEGIN,
        COMMIT,
        ABORT,
        DISCONNECT,
        CONNECT,
        RECEIPT,
        CONNECTED,
        ERROR,
        ACK,
        MESSAGE
    }

    public static final String HEADER_DESTINATION = "destination";
    public static final String HEADER_REPLY_TO = "reply-to";
    public static final String HEADER_ACCEPT = "accept-version";
    public static final String HEADER_ID = "id";
    public static final String HEADER_MESSAGE = "message";
    public static final String HEADER_ACK = "ack";
    public static final String HEADER_TRANSACTION = "transaction";
    public static final String HEADER_RECEIPT = "receipt";
    public static final String HEADER_RECEIPT_ID = "receipt-id";
    public static final String HEADER_CONTENT_LENGTH = "content-length";
    public static final String HEADER_CONTENT_TYPE = "content-type";
    public static final String HEADER_HEART_BEAT = "heart-beat";
    public static final String HEADER_HOST = "host";
    public static final String HEADER_CORRELATION_ID = "ovirtCorrelationId";
    public static final String END_OF_MESSAGE = "\000";
    public static final byte[] HEARTBEAT_FRAME = "\n".getBytes();
    private static final String CHARSET = ";charset=";
    private static final Logger LOG = LoggerFactory.getLogger(Message.class);
    private String command;
    private Map<String, String> headers = new HashMap<>();
    private byte[] content = new byte[0];

    public Message withHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public Message withHeaders(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
    }

    public Message withContent(byte[] content) {
        this.content = content;
        return this;
    }

    public Message withCorrelationId() {
        String correlationId = MDC.get(HEADER_CORRELATION_ID);
        if (StringUtils.isNotBlank(correlationId)) {
            withHeader(HEADER_CORRELATION_ID, correlationId);
        }
        return this;
    }

    public Message withAdditionalContent(byte[] additional) {
        byte[] result = new byte[this.content.length + additional.length];
        System.arraycopy(this.content, 0, result, 0, this.content.length);
        System.arraycopy(additional, 0, result, this.content.length, additional.length);

        this.content = result;
        return this;
    }

    public Message send() {
        this.command = Command.SEND.toString();
        return this;
    }

    public Message ack() {
        this.command = Command.ACK.toString();
        return this;
    }

    public Message subscribe() {
        this.command = Command.SUBSCRIBE.toString();
        return this;
    }

    public Message unsubscribe() {
        this.command = Command.UNSUBSCRIBE.toString();
        return this;
    }

    public Message begin() {
        this.command = Command.BEGIN.toString();
        return this;
    }

    public Message commit() {
        this.command = Command.COMMIT.toString();
        return this;
    }

    public Message abort() {
        this.command = Command.ABORT.toString();
        return this;
    }

    public Message disconnect() {
        this.command = Command.DISCONNECT.toString();
        return this;
    }

    public Message connect() {
        this.command = Command.CONNECT.toString();
        return this;
    }

    public Message receipt() {
        this.command = Command.RECEIPT.toString();
        return this;
    }

    public Message connected() {
        this.command = Command.CONNECTED.toString();
        return this;
    }

    public Message error() {
        this.command = Command.ERROR.toString();
        return this;
    }

    public Message message() {
        this.command = Command.MESSAGE.toString();
        return this;
    }

    private Message setCommand(String command) {
        this.command = command;
        return this;
    }

    public byte[] build() {
        if (isEmpty(this.command)) {
            throw new IllegalArgumentException("Command can't be empty");
        }
        StringBuilder builder = new StringBuilder(this.command);
        builder.append("\n");

        for (String key : this.headers.keySet()) {
            builder.append(key);
            builder.append(":");
            builder.append(this.headers.get(key));
            builder.append("\n");
        }
        if (this.content.length != 0) {
            builder.append(HEADER_CONTENT_LENGTH).append(":").append(this.content.length).append("\n");
        }
        builder.append("\n");

        if (this.content.length != 0) {
            builder.append(new String(this.content, getEncoding()));
        }

        builder.append(END_OF_MESSAGE);

        return builder.toString().getBytes(UTF8);
    }

    public String getCommand() {
        return command;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getContent() {
        return content;
    }

    public static Message parse(byte[] array) throws ClientConnectionException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Raw message received: {}", new String(array, UTF8));
        }
        String[] message = new String(array, UTF8).split("\n");

        if (message.length == 0) {
            return null;
        }

        int line = 0;
        String msg = message[line];
        while (msg.length() == 0) {
            line++;
            msg = message[line];
        }

        Message result = new Message();
        if (message.length - line <= 2) {
            // heart-beat
            return null;
        }
        try {
            String command = message[line].replaceAll(END_OF_MESSAGE, "");
            Command parsedCommand = Command.valueOf(command);
            result.setCommand(parsedCommand.toString());
        } catch (IllegalArgumentException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Message received: " + new String(array, UTF8));
            }
            throw new ClientConnectionException("Unrecognized message received ");
        }

        Map<String, String> headers = new HashMap<>();
        line++;
        String currentLine = message[line];
        while (currentLine.length() > 0) {
            int ind = currentLine.indexOf(':');
            String key = currentLine.substring(0, ind);
            String value = currentLine.substring(ind + 1);
            headers.put(key, value);
            currentLine = message[++line];
        }
        result.withHeaders(headers);
        // ignore blank line between headers and content
        line++;
        result.withContent(getContent(array, message, line));
        return result;
    }

    private static byte[] getContent(byte[] array, String[] message, int lineNumber) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lineNumber; i++) {
            builder.append(message[i]);
            builder.append("\n");
        }
        return Arrays.copyOfRange(array, builder.toString().getBytes(UTF8).length, array.length);
    }

    public Charset getEncoding() {
        Charset result = UTF8;
        String contentType = getHeaders().get(HEADER_CONTENT_TYPE);
        if (contentType != null) {
            int idx = contentType.indexOf(CHARSET);
            if (idx != -1) {
                try {
                    result = Charset.forName(contentType.substring(idx + 1));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return result;
    }

    public int getContentLength() {
        String length = getHeaders().get(HEADER_CONTENT_LENGTH);
        int contentLength = -1;
        if (length != null) {
            try {
                contentLength = Integer.parseInt(length);
            } catch (NumberFormatException ignored) {
            }
        }
        return contentLength;
    }

    public void trimEndOfMessage() {
        this.content = Arrays.copyOfRange(this.content, 0, this.content.length - 1);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.command).append("\n");
        for (String key : this.headers.keySet()) {
            builder.append(key).append(":").append(this.headers.get(key)).append("\n");
        }
        builder.append("\n");
        if (Command.SEND.toString().equals(this.command)) {
            JsonRpcRequest request = JsonRpcRequest.fromByteArray(this.content);
            builder.append(request.toString());
        } else if (Command.MESSAGE.toString().equals(this.command)) {
            JsonRpcResponse response = JsonRpcResponse.fromByteArray(this.content);
            builder.append(response.toString());
        }
        return builder.toString();
    }
}
