package org.ovirt.vdsm.jsonrpc.client.events;

import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.ALL;
import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;

import org.ovirt.vdsm.jsonrpc.client.JsonRpcEvent;
import org.ovirt.vdsm.jsonrpc.client.utils.LockWrapper;

/**
 * Holds subscription information such as amount of messages requested by {@link EventSubscriber}. When events are not
 * processed immediately they are queued in here. This holder contains instance of subscription itself.
 *
 */
public class SubscriptionHolder {
    public static final LongUnaryOperator DECREMENT_ONLY_POSITIVE = currentValue -> {
        if (currentValue > 0) {
            return currentValue - 1;
        }
        return 0;
    };
    private EventSubscriber subscriber;
    private Deque<JsonRpcEvent> events = new ConcurrentLinkedDeque<>();
    private volatile AtomicLong count;
    private String[] parsedId;
    private List<String> filteredId;
    private Lock lock = new ReentrantLock();

    /**
     * Creates a holder which subscriber instance and count and it prepares subscription id representation for event
     * matching.
     *
     * @param subscriber
     *            Instance of @link {@link EventSubscriber}.
     * @param count
     *            Represent current number of events requested by subscriber.
     */
    public SubscriptionHolder(EventSubscriber subscriber, AtomicLong count) {
        this.subscriber = subscriber;
        this.count = count;
        this.parsedId = parse(getId());
        filter();
    }

    /**
     * @return subscription id as complete string e.q.
     * &lt;receiver&gt;.&lt;component&gt;.&lt;operation_id&gt;.&lt;unique_id&gt;.
     */
    public String getId() {
        return this.subscriber.getSubscriptionId();
    }

    /**
     * @return parsed subscription id as string array. Each entry represents subscription type.
     */
    public String[] getParsedId() {
        return this.parsedId;
    }

    private void filter() {
        String[] ids = this.getParsedId();
        this.filteredId = Arrays.stream(ids)
                .filter(id -> !ALL.equals(id))
                .collect(Collectors.toList());
    }

    /**
     * @return Filtered subscription id which do not contains all filter '*'
     */
    public List<String> getFilteredId() {
        return new ArrayList<String>(this.filteredId);
    }

    /**
     * @return Checks and return information whether subscriber can process events based on count defined.
     */
    public boolean canProcess() {
        return this.count.get() > 0;
    }

    /**
     * @return An event for processing if there is any and if subscriber is willing to process more events.
     */
    public JsonRpcEvent canProcessMore() {
        try (LockWrapper wrapper = new LockWrapper(this.lock)) {
            if (!this.events.isEmpty() && this.count.getAndUpdate(DECREMENT_ONLY_POSITIVE) > 0) {
                return this.events.removeLast();
            }
            return null;
        }
    }

    /**
     * Queues not processed event for later processing. When adding an event to the queue, set the arrival time of the
     * event.
     *
     * @param event
     *            An event to be queued.
     */
    public void putEvent(JsonRpcEvent event) {
        try (LockWrapper wrapper = new LockWrapper(this.lock)) {
            event.setArrivalTime(System.nanoTime());
            this.events.addFirst(event);
        }
    }

    /*
     * (non-Javadoc)
     *
     * Used by test case to count number of events
     */
    public int getNumberOfEvents() {
        try (LockWrapper wrapper = new LockWrapper(this.lock)) {
            return this.events.size();
        }
    }

    /**
     * Purge old events if they have not been consumed in a specified amount of time.
     * @param eventTimeoutInHours the timeout after which the events are purged from the queue.
     */
    public void purgeOldEventsIfNotConsumed(int eventTimeoutInHours) {
        try (LockWrapper wrapper = new LockWrapper(this.lock)) {
            long threshold = System.nanoTime() - TimeUnit.HOURS.toNanos(eventTimeoutInHours);
            // remove the last element if the element was created before threshold
            while (!this.events.isEmpty() && this.events.peekLast().getArrivalTime() < threshold) {
                // if the event is older than PURGE_TIME we remove the event
                this.events.removeLast();
            }
        }
    }

    /**
     * @return Subscribed hold by this instance.
     */
    public EventSubscriber getSubscriber() {
        return this.subscriber;
    }

    /**
     * Clean event queue.
     */
    public void clean() {
        try (LockWrapper wrapper = new LockWrapper(this.lock)) {
            this.events.clear();
        }
    }
}
