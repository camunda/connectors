package io.camunda.connector.kafkainbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class KafkaSubscription {
    private final static Logger LOG = LoggerFactory.getLogger(KafkaSubscription.class);

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private final EventGenerator generator;

    public KafkaSubscription(String sender, int messagesPerMinute, Consumer<KafkaSubscriptionEvent> callback) {
        LOG.info("Activating mock subscription");
        generator = new EventGenerator(sender);
        executor.scheduleAtFixedRate(
                () ->
                        produceEvent(callback), 5, 60 / messagesPerMinute, TimeUnit.SECONDS);
    }

    public void stop() {
        LOG.info("Deactivating mock subscription");
        executor.shutdownNow();
    }

    private void produceEvent(Consumer<KafkaSubscriptionEvent> callback) {
        KafkaSubscriptionEvent event = generator.getRandomEvent();
        LOG.info("Emulating subscription event: " + event);
        callback.accept(event);
    }

    private static class EventGenerator {
        private final String sender;
        private final int MAX_CODE = 10;

        EventGenerator(String sender) {
            this.sender = sender;
        }

        private final Random random = new Random();

        public KafkaSubscriptionEvent getRandomEvent() {
            int code = random.nextInt(MAX_CODE);
            String message = UUID.randomUUID().toString();
            return new KafkaSubscriptionEvent(sender, code, message);
        }
    }
}
