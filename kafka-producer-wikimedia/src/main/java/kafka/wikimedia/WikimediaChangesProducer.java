package kafka.wikimedia;

import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.EventHandler;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.internals.Topic;
import org.apache.kafka.common.serialization.StringSerializer;

import java.net.URI;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class WikimediaChangesProducer {
    public static void main(String[] args) throws InterruptedException {

        String bootstrapServers = "127.0.0.1:9092";

        // Create producer properties
        Properties properties = new Properties();

        // Key-value pairs
        // Can set as many properties as we want
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Set producer properties
        // Basically formatting
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        ////////////////////////////////////////// SAFE PRODUCER SETTINGS //////////////////////////////////////////
        // Enabled by default for me because it is 3.5.0

        // If idempotence, try once, commit and ack
        // Try twice, network error
        // Try thrice, will see that data is already duplicated, will not commit but send an ack
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

        // Acknowledge after every commit
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all"); // Same as -1

        // Retry "infinite times" until a delivery.timeout.ms is reached
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));

        ////////////////////////////////////////// SAFE PRODUCER SETTINGS //////////////////////////////////////////

        // -------------------------------- Message compression at Producer level -------------------------------- \\
        // Advantages:
        // Smaller producer request size
        // Faster to transfer data

        // ----------------------------- Message compression at Broker/Topic level ----------------------------- \\
        // Can set to all topics or let it be topic-level
        // Extra CPU cycles

        // Batching is good, helps with compression as well \\
        // linger.ms = default 0 How long to wait until we send a batch
        // batch.size = if batch is filled before l.m, increase batch size
        // Make batch until linger.ms, send it after ms time
        // Batch size \\
        // Any message bigger than size, gets sent right away
        // SNAPPY \\

        // Set high throughput producer configs
        properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20"); // At expense of 20 miliseconds
        properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32 * 1024));
        properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Key Hashing determines map of key to a partition
        // Murmur2 algo is used to determine keys because it is predictable
        // Key = NULL, then because its Kafka 3.5, the default way of partitioning is by using sticky partitioner // Larger batches, less latency
        // If its <=v2.3, then the round-robin method is used // Results in more batches, not optimal

        // Create the producer
        // Passing properties as a constructor param
        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

        // Topic to send data to
        String topic = "wikimedia.recentchange";

        EventHandler eventHandler = new WikimediaChangeHandler(producer, topic);

        String url = "https://stream.wikimedia.org/v2/stream/recentchange";
        EventSource.Builder builder = new EventSource.Builder(eventHandler, URI.create(url));
        EventSource eventSource = builder.build();

        // Start producer in another thread
        eventSource.start();

        // Can produce for 10 minutes and block the program until then
        TimeUnit.MINUTES.sleep(10);

    }
}
