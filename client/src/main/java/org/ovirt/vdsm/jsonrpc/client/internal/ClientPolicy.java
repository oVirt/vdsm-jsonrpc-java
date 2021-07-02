package org.ovirt.vdsm.jsonrpc.client.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java bean which provide information how retry logic should work.
 *
 */
public class ClientPolicy {
    private final int retryTimeOut;
    private final int retryNumber;
    private final List<Class<? extends Exception>> exceptions;
    private final AtomicBoolean isIncomingHeartbeat;
    private final AtomicBoolean isOutgoingHeartbeat;
    private volatile int incomingHeartbeat;
    private volatile int outgoingHeartbeat;

    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private String identifier;

    /**
     * Create policy using provided values.
     *
     * @param retryTimeOut
     *            - <code>Integer</code> value which is used as timeout between operation retry combined with
     *            <code>TimeUnit</code> which is set to milliseconds by default.
     * @param retryNumber
     *            - <code>Integer</code> value which defines number of retry attempts.
     * @param incomingHeartbeat
     *            - <code>Integer</code> value which defines incoming heart beat.
     * @param outgoingHeartbeat
     *            - <code>Integer</code> value which defines outgoing heart beat.
     * @param retryableExceptions
     *            - <code>List</code> of retryable exceptions.
     */
    public ClientPolicy(int retryTimeOut, int retryNumber, int incomingHeartbeat,
            int outgoingHeartbeat, List<Class<? extends Exception>> retryableExceptions) {
        this.retryNumber = retryNumber;
        this.retryTimeOut = retryTimeOut;
        this.isIncomingHeartbeat = new AtomicBoolean();
        this.isOutgoingHeartbeat = new AtomicBoolean();
        setIncomingHeartbeat(incomingHeartbeat);
        setOutgoingHeartbeat(outgoingHeartbeat);
        this.exceptions = Collections.unmodifiableList(retryableExceptions);
    }

    public ClientPolicy(int retryTimeOut, int retryNumber, int incomingHeartbeat) {
        this(retryTimeOut, retryNumber, incomingHeartbeat, 0, new ArrayList<>());
    }

    public ClientPolicy(int retryTimeOut, int retryNumber, int incomingHeartbeat, int outgoingHeartbeat) {
        this(retryTimeOut,
                retryNumber,
                incomingHeartbeat,
                outgoingHeartbeat,
                new ArrayList<>());
    }

    public ClientPolicy(int retryTimeOut,
            int retryNumber,
            int incomingHeartbeat,
            Class<? extends Exception> retryableException) {
        this(retryTimeOut,
                retryNumber,
                incomingHeartbeat,
                0,
                new ArrayList<>(List.of(retryableException)));
    }

    public ClientPolicy(int retryTimeOut,
            int retryNumber,
            int incomingHeartbeat,
            int outgoingHeartbeat,
            Class<? extends Exception> retryableException) {
        this(retryTimeOut,
                retryNumber,
                incomingHeartbeat,
                outgoingHeartbeat,
                new ArrayList<>(List.of(retryableException)));
    }

    public int getRetryTimeOut() {
        return this.retryTimeOut;
    }

    public int getRetryNumber() {
        return this.retryNumber;
    }

    public int getIncomingHeartbeat() {
        return this.incomingHeartbeat;
    }

    public int getOutgoingHeartbeat() {
        return this.outgoingHeartbeat;
    }

    public final void setOutgoingHeartbeat(int outgoingHeartbeat) {
        this.outgoingHeartbeat = outgoingHeartbeat;
        this.isOutgoingHeartbeat.set(outgoingHeartbeat != 0);
    }

    public final void setIncomingHeartbeat(int incomingHeartbeat) {
        this.incomingHeartbeat = incomingHeartbeat;
        this.isIncomingHeartbeat.set(incomingHeartbeat != 0);
    }

    public List<Class<? extends Exception>> getExceptions() {
        return this.exceptions;
    }

    public TimeUnit getTimeUnit() {
        return this.timeUnit;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier){
        this.identifier = identifier;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public boolean isIncomingHeartbeat() {
        return this.isIncomingHeartbeat.get();
    }

    public void setIncomingHeartbeat(boolean isHeartbeat) {
        this.isIncomingHeartbeat.set(isHeartbeat && this.incomingHeartbeat != 0);
    }

    public boolean isOutgoingHeartbeat() {
        return this.isOutgoingHeartbeat.get();
    }

    public void setOutgoingHeartbeat(boolean isHeartbeat) {
        this.isOutgoingHeartbeat.set(isHeartbeat && this.outgoingHeartbeat != 0);
    }

    @Override
    public ClientPolicy clone() {
        return new ClientPolicy(this.retryTimeOut, this.retryNumber, this.incomingHeartbeat, this.outgoingHeartbeat,
                this.exceptions);
    }
}
