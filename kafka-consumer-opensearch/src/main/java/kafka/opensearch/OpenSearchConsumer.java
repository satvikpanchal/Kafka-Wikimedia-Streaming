package kafka.opensearch;

import com.fasterxml.jackson.dataformat.yaml.util.StringQuotingChecker;
import org.apache.http.HttpHost;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.apache.http.auth.AuthScope;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class OpenSearchConsumer {

    public static RestHighLevelClient createOpenSearchClient() {
        // Docker
        String connString = "http://localhost:9200";
        // Cloud

        // we build a URI from the connection string
        RestHighLevelClient restHighLevelClient;
        URI connUri = URI.create(connString);
        // extract login information if it exists
        String userInfo = connUri.getUserInfo();

        if (userInfo == null) {
            // REST client without security
            restHighLevelClient = new RestHighLevelClient(RestClient.builder(new HttpHost(connUri.getHost(), connUri.getPort(), "http")));

        } else {
            // REST client with security
            String[] auth = userInfo.split(":");

            CredentialsProvider cp = new BasicCredentialsProvider();
            cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(auth[0], auth[1]));

            restHighLevelClient = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(connUri.getHost(), connUri.getPort(), connUri.getScheme()))
                            .setHttpClientConfigCallback(
                                    httpAsyncClientBuilder -> httpAsyncClientBuilder.setDefaultCredentialsProvider(cp)
                                            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())));


        }

        return restHighLevelClient;
    }

    private static KafkaConsumer<String, String> createKafkaConsumer()
    {
        String bootstrapServers = "127.0.0.1:9092";
        String groupID = "consumer-opensearch-demo";

        // Create producer properties
        Properties properties = new Properties();

        // Key-value pairs
        // Can set as many properties as we want
        properties.setProperty("bootstrap.servers", bootstrapServers);

        // Create consumer configs
        properties.setProperty("key.deserializer", StringDeserializer.class.getName());
        properties.setProperty("value.deserializer", StringDeserializer.class.getName());

        properties.setProperty("group.id", groupID);

        // None - If no consumer group, then fail...must set first
        // Earliest - Read from beginning of topic
        // Latest - Read only new messages
        properties.setProperty("auto.offset.reset", "latest");

        return new KafkaConsumer<>(properties);
    }

    public static void main(String[] args) throws IOException {
        // Logger to keep track of logs
        Logger log = LoggerFactory.getLogger(OpenSearchConsumer.class.getSimpleName());

        // Create an OpenSearch Client
        RestHighLevelClient openSearchClient = createOpenSearchClient();

        // Create our Kafka Client
        KafkaConsumer<String, String> consumer = createKafkaConsumer();

        // Create the index on OpenSearch if it doesn't exist already
        // Instead of closing, we can do try

        try(openSearchClient; consumer)
        {
            // To check if we already have created the index in open search
            boolean indexExists = openSearchClient.indices().exists(new GetIndexRequest("wikimedia"), RequestOptions.DEFAULT);

            if(indexExists == false)
            {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest("wikimedia");
                openSearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
                log.info("Index created!");
            }
            else{
                log.info("Index already exists :(");
            }

            // Subscribing to consumers
            consumer.subscribe(Collections.singleton("wikimedia.recentchange"));

            while(true)
            {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(3000));

                int recordCount = records.count();
                log.info("Received " + recordCount + " record(s)");

                for(ConsumerRecord<String, String> record : records)
                {

                    // Send record int OpenSearch

                    try {
                        IndexRequest indexRequest = new IndexRequest("wikimedia")
                                .source(record.value(), XContentType.JSON);

                        // Send it to openSearch
                        IndexResponse response = openSearchClient.index(indexRequest, RequestOptions.DEFAULT);

                        // Logs purposes
                        log.info(response.getId());
                    } catch (Exception e){

                    }

                }
            }

        }

        // Main code logic

        // Close things

    }

}
