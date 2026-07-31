package org.wang.rabbitmqlab.demo06_publisherConfirms;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.time.Duration;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BooleanSupplier;

public class PublisherConfirms {
    static final int MESSAGE_COUNT = 50_000;

    static final int MAX_OUTSTANDING = 1000;
    static final int THROTTLING_PERCENTAGE = 50;
    static final int MAX_DELAY_MS = 100;

    static Connection createConnection() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.6.132");
        factory.setPort(5672);
        factory.setUsername("admin");
        factory.setPassword("passw0rd");
        factory.setVirtualHost("/mirror");
        return factory.newConnection();
    }

    static void publishMessagesIndividually() throws Exception {
        // Implementation for publishing messages individually
        try (Connection connection = createConnection()) {
            Channel channel = connection.createChannel();
            channel.confirmSelect(); // Enable publisher confirms
            String queue = UUID.randomUUID().toString();
            channel.queueDeclare(queue, false, false, true, null);
            long start = System.nanoTime();
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                String body = String.valueOf(i);
                channel.basicPublish("", queue, null, body.getBytes());
                channel.waitForConfirmsOrDie(5000); // Wait for confirmatio// n
            }
            long end = System.nanoTime();
            System.out.format("Published %,d messages individually in %,d ms%n", MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
        }
    }

    static void publishMessagesInBatches() throws Exception {
        // Implementation for publishing messages in batches
        try (Connection connection = createConnection()) {
            Channel channel = connection.createChannel();
            channel.confirmSelect();
            String queue = UUID.randomUUID().toString();
            channel.queueDeclare(queue, false, false, true, null);
            int batchSize = 100;
            int outstandingMessageCount = 0;
            long start = System.nanoTime();
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                String body = String.valueOf(i);
                channel.basicPublish("", queue, null, body.getBytes());
                outstandingMessageCount++;
                if (outstandingMessageCount == batchSize) {
                    channel.waitForConfirmsOrDie(5000);
                    outstandingMessageCount = 0;
                }
            }
            if (outstandingMessageCount > 0) {
                channel.waitForConfirmsOrDie(5000);
            }
            long end = System.nanoTime();
            System.out.format("Published %,d messages in batch in %,d ms%n", MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
        }
    }

    static void handlePublishConfirmsAsynchronously() throws Exception {
        try (Connection connection = createConnection()) {
            Channel ch = connection.createChannel();

            String queue = UUID.randomUUID().toString();
            ch.queueDeclare(queue, false, false, true, null);

            ch.confirmSelect();

            ConcurrentNavigableMap<Long, String> outstandingConfirms = new ConcurrentSkipListMap<>();

            ConfirmCallback cleanOutstandingConfirms = (sequenceNumber, multiple) -> {
                if (multiple) {
                    ConcurrentNavigableMap<Long, String> confirmed = outstandingConfirms.headMap(
                            sequenceNumber, true
                    );
                    confirmed.clear();
                } else {
                    outstandingConfirms.remove(sequenceNumber);
                }
            };

            ch.addConfirmListener(cleanOutstandingConfirms, (sequenceNumber, multiple) -> {
                String body = outstandingConfirms.get(sequenceNumber);
                System.err.format(
                        "Message with body %s has been nack-ed. Sequence number: %d, multiple: %b%n",
                        body, sequenceNumber, multiple
                );
                cleanOutstandingConfirms.handle(sequenceNumber, multiple);
            });

            long start = System.nanoTime();
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                String body = String.valueOf(i);
                outstandingConfirms.put(ch.getNextPublishSeqNo(), body);
                ch.basicPublish("", queue, null, body.getBytes());
            }

            if (!waitUntil(Duration.ofSeconds(60), outstandingConfirms::isEmpty)) {
                throw new IllegalStateException("All messages could not be confirmed in 60 seconds");
            }

            long end = System.nanoTime();
            System.out.format("Published %,d messages and handled confirms asynchronously in %,d ms%n", MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
        }
    }

    static void handlePublishConfirmsWithWindow() throws Exception {
        try (Connection connection = createConnection()) {
            Channel ch = connection.createChannel();

            String queue = UUID.randomUUID().toString();
            ch.queueDeclare(queue, false, false, true, null);
            ch.confirmSelect();

            ConcurrentNavigableMap<Long, String> outstandingConfirms = new ConcurrentSkipListMap<>();

            ConfirmCallback cleanOutstandingConfirms = (sequenceNumber, multiple) -> {
                if (multiple) {
                    outstandingConfirms.headMap(sequenceNumber, true).clear();
                } else {
                    outstandingConfirms.remove(sequenceNumber);
                }
                synchronized (outstandingConfirms) {
                    outstandingConfirms.notifyAll();
                }
            };

            ch.addConfirmListener(cleanOutstandingConfirms, (sequenceNumber, multiple) -> {
                System.err.format("Message nacked. Sequence: %d, multiple: %b%n", sequenceNumber, multiple);
                cleanOutstandingConfirms.handle(sequenceNumber, multiple);
            });

            long start = System.nanoTime();
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                // Wait if window is full
                synchronized (outstandingConfirms) {
                    while (outstandingConfirms.size() >= MAX_OUTSTANDING) {
                        outstandingConfirms.wait();
                    }
                }

                String body = String.valueOf(i);
                outstandingConfirms.put(ch.getNextPublishSeqNo(), body);
                ch.basicPublish("", queue, null, body.getBytes());
            }

            // Wait for remaining confirmations
            synchronized (outstandingConfirms) {
                while (!outstandingConfirms.isEmpty()) {
                    outstandingConfirms.wait();
                }
            }

            long end = System.nanoTime();
            System.out.format("Published %,d messages with confirmation window in %,d ms%n",
                    MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
        }
    }

    static void handlePublishConfirmsWithAdaptiveThrottling() throws Exception {
        try (Connection connection = createConnection()) {
            Channel ch = connection.createChannel();

            String queue = UUID.randomUUID().toString();
            ch.queueDeclare(queue, false, false, true, null);
            ch.confirmSelect();

            LinkedList<Long> outstandingConfirms = new LinkedList<>();
            int throttlingThreshold = MAX_OUTSTANDING * THROTTLING_PERCENTAGE / 100;

            ConfirmCallback cleanOutstandingConfirms = (sequenceNumber, multiple) -> {
                synchronized (outstandingConfirms) {
                    if (multiple) {
                        outstandingConfirms.removeIf(seqNo -> seqNo <= sequenceNumber);
                    } else {
                        outstandingConfirms.removeFirstOccurrence(sequenceNumber);
                    }
                    outstandingConfirms.notifyAll();
                }
            };

            ch.addConfirmListener(cleanOutstandingConfirms, (sequenceNumber, multiple) -> {
                System.err.format("Message nacked. Sequence: %d, multiple: %b%n", sequenceNumber, multiple);
                cleanOutstandingConfirms.handle(sequenceNumber, multiple);
            });

            long start = System.nanoTime();
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                String body = String.valueOf(i);

                synchronized (outstandingConfirms) {
                    while (outstandingConfirms.size() >= MAX_OUTSTANDING) {
                        outstandingConfirms.wait();
                    }

                    int availablePermits = MAX_OUTSTANDING - outstandingConfirms.size();
                    if (availablePermits < throttlingThreshold) {
                        double percentageUsed = 1.0 - (availablePermits / (double) MAX_OUTSTANDING);
                        int delay = (int) (percentageUsed * MAX_DELAY_MS);
                        if (delay > 0) {
                            outstandingConfirms.wait(delay);
                        }
                    }

                    long seqNo = ch.getNextPublishSeqNo();
                    outstandingConfirms.addLast(seqNo);
                }

                ch.basicPublish("", queue, null, body.getBytes());
            }

            synchronized (outstandingConfirms) {
                while (!outstandingConfirms.isEmpty()) {
                    outstandingConfirms.wait();
                }
            }

            long end = System.nanoTime();
            System.out.format("Published %,d messages with adaptive throttling in %,d ms%n",
                    MESSAGE_COUNT, Duration.ofNanos(end - start).toMillis());
        }
    }

    static boolean waitUntil(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        int waited = 0;
        while (!condition.getAsBoolean() && waited < timeout.toMillis()) {
            Thread.sleep(100L);
            waited += 100;
        }
        return condition.getAsBoolean();
    }
}
