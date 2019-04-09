package org.ovirt.vdsm.jsonrpc.client.events;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.ovirt.vdsm.jsonrpc.client.events.EventTestUtls.createPublisher;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.ovirt.vdsm.jsonrpc.client.EventDecomposer;
import org.ovirt.vdsm.jsonrpc.client.JsonRpcEvent;
import org.reactivestreams.Subscription;

public class PublisherTestCase {

    private static final int EVENT_TIMEOUT_IN_HOURS = 10;

    @Test
    public void testSingleMsg() throws NoSuchFieldException, SecurityException, IllegalArgumentException,
            IllegalAccessException {
        EventPublisher publisher = createPublisher();

        JsonRpcEvent event = mock(JsonRpcEvent.class);
        when(event.getMethod()).thenReturn("local|testcase|test|uuid");
        when(event.getArrivalTime()).thenReturn(System.nanoTime());

        EventDecomposer decomposer = mock(EventDecomposer.class);
        Map<String, Object> map = new HashMap<>();
        when(decomposer.decompose(event)).thenReturn(map);
        setField(publisher, "decomposer", decomposer);

        EventSubscriber subscriber = mock(EventSubscriber.class);
        when(subscriber.getSubscriptionId()).thenReturn("*|*|*|uuid");
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);

        publisher.subscribe(subscriber);
        verify(subscriber).onSubscribe(captor.capture());

        Subscription subscription = captor.getValue();

        publisher.process(event);
        verify(subscriber, timeout(500).times(0)).onNext(map);

        subscription.request(1);
        verify(subscriber, timeout(500).times(1)).onNext(map);

        subscription.cancel();
        verify(subscriber, timeout(500).times(1)).onComplete();
    }

    @Test
    public void testMultipleMsg() throws NoSuchFieldException, SecurityException, IllegalArgumentException,
            IllegalAccessException {
        EventPublisher publisher = createPublisher();

        JsonRpcEvent event = mock(JsonRpcEvent.class);
        when(event.getMethod()).thenReturn("local|testcase|test|uuid");
        when(event.getArrivalTime()).thenReturn(System.nanoTime());

        EventDecomposer decomposer = mock(EventDecomposer.class);
        Map<String, Object> map = new HashMap<>();
        when(decomposer.decompose(event)).thenReturn(map);
        setField(publisher, "decomposer", decomposer);

        EventSubscriber subscriber = mock(EventSubscriber.class);
        when(subscriber.getSubscriptionId()).thenReturn("*|*|test|*");
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);

        publisher.subscribe(subscriber);
        verify(subscriber).onSubscribe(captor.capture());

        Subscription subscription = captor.getValue();

        subscription.request(10);

        for (int i = 0; i < 15; i++) {
            publisher.process(event);
        }

        verify(subscriber, timeout(1000).times(10)).onNext(map);
    }

    @Test
    public void testCancelledSubscription() throws NoSuchFieldException, SecurityException, IllegalArgumentException,
            IllegalAccessException {
        EventPublisher publisher = createPublisher();

        JsonRpcEvent event = mock(JsonRpcEvent.class);
        when(event.getMethod()).thenReturn("local|testcase|test|uuid");
        when(event.getArrivalTime()).thenReturn(System.nanoTime());

        EventDecomposer decomposer = mock(EventDecomposer.class);
        Map<String, Object> map = new HashMap<>();
        when(decomposer.decompose(event)).thenReturn(map);
        setField(publisher, "decomposer", decomposer);

        EventSubscriber subscriber = mock(EventSubscriber.class);
        when(subscriber.getSubscriptionId()).thenReturn("*|*|test|*");
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);

        publisher.subscribe(subscriber);
        verify(subscriber).onSubscribe(captor.capture());

        Subscription subscription = captor.getValue();
        subscription.cancel();

        publisher.process(event);

        verify(subscriber, never()).onNext(map);
        verify(subscriber, times(1)).onComplete();
    }

    @Test
    public void testOneMsgPurged() throws NoSuchFieldException, SecurityException, IllegalArgumentException,
            IllegalAccessException {
        EventPublisher publisher = createPublisher();

        JsonRpcEvent event1 = createEvent(System.nanoTime() - TimeUnit.HOURS.toNanos(EVENT_TIMEOUT_IN_HOURS+1));
        JsonRpcEvent event2 = createEvent(System.nanoTime());

        EventDecomposer decomposer = mock(EventDecomposer.class);
        Map<String, Object> map = new HashMap<>();
        when(decomposer.decompose(event1)).thenReturn(map);
        when(decomposer.decompose(event2)).thenReturn(map);
        setField(publisher, "decomposer", decomposer);

        EventSubscriber subscriber = mock(EventSubscriber.class);
        when(subscriber.getSubscriptionId()).thenReturn("*|*|*|uuid");
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);

        publisher.subscribe(subscriber);
        verify(subscriber).onSubscribe(captor.capture());

        publisher.process(event1);
        publisher.process(event2);
        publisher.cleanupOldEvents();
        int numberOfEvents = publisher.countEvents(event2);
        assertEquals(1, numberOfEvents);
    }

    @Test
    public void testMultipleMsgsPurged() throws NoSuchFieldException, SecurityException, IllegalArgumentException,
            IllegalAccessException {
        EventPublisher publisher = createPublisher();

        JsonRpcEvent event1 = createEvent(System.nanoTime() - TimeUnit.HOURS.toNanos(EVENT_TIMEOUT_IN_HOURS+1));
        JsonRpcEvent event2 = createEvent(System.nanoTime() - TimeUnit.HOURS.toNanos(EVENT_TIMEOUT_IN_HOURS+1));
        JsonRpcEvent event3 = createEvent(System.nanoTime() - TimeUnit.HOURS.toNanos(EVENT_TIMEOUT_IN_HOURS+1));
        JsonRpcEvent event4 = createEvent(System.nanoTime() - TimeUnit.HOURS.toNanos(EVENT_TIMEOUT_IN_HOURS+1));
        JsonRpcEvent event5 = createEvent(System.nanoTime() - TimeUnit.HOURS.toNanos(EVENT_TIMEOUT_IN_HOURS+1));
        JsonRpcEvent event6 = createEvent(System.nanoTime());
        JsonRpcEvent event7 = createEvent(System.nanoTime());

        EventDecomposer decomposer = mock(EventDecomposer.class);
        Map<String, Object> map = new HashMap<>();
        when(decomposer.decompose(event1)).thenReturn(map);
        when(decomposer.decompose(event2)).thenReturn(map);
        when(decomposer.decompose(event3)).thenReturn(map);
        when(decomposer.decompose(event4)).thenReturn(map);
        when(decomposer.decompose(event5)).thenReturn(map);
        when(decomposer.decompose(event6)).thenReturn(map);
        when(decomposer.decompose(event7)).thenReturn(map);
        setField(publisher, "decomposer", decomposer);

        EventSubscriber subscriber = mock(EventSubscriber.class);
        when(subscriber.getSubscriptionId()).thenReturn("*|*|*|uuid");
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);

        publisher.subscribe(subscriber);
        verify(subscriber).onSubscribe(captor.capture());

        publisher.process(event1);
        publisher.process(event2);
        publisher.process(event3);
        publisher.process(event4);
        publisher.process(event5);
        publisher.process(event6);
        publisher.process(event7);
        publisher.cleanupOldEvents();
        int numberOfEvents = publisher.countEvents(event7);
        assertEquals(2, numberOfEvents);
    }

    private JsonRpcEvent createEvent(long arrivalTime) {
        JsonRpcEvent event = mock(JsonRpcEvent.class);
        when(event.getMethod()).thenReturn("local|testcase|test|uuid");
        when(event.getArrivalTime()).thenReturn(arrivalTime);
        return event;
    }

    public static void setField(Object obj, String fieldName, Object value) throws NoSuchFieldException,
            SecurityException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }

}
