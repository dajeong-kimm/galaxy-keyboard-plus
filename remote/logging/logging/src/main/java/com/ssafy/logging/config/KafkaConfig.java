package com.ssafy.logging.config;

import com.ssafy.logging.dto.ServerUsageEvent;
import com.ssafy.logging.dto.TokenUsageEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

import java.util.List;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.bootstrap-servers}")
    private String bootstrapServers;
    @Value("${app.kafka.group-id}")
    private String groupId;

    @Bean
    public ReceiverOptions<String, TokenUsageEvent> tokenReceiverOptions(
            @Value("${app.kafka.bootstrap-servers}") String brokers,
            @Value("${app.kafka.group-id}") String groupId
    ) {
        Map<String,Object> props = Map.of(
                "bootstrap.servers", brokers,
                "group.id", groupId,
                "key.deserializer", StringDeserializer.class,
                "value.deserializer", JsonDeserializer.class,
                JsonDeserializer.TRUSTED_PACKAGES, "com.ssafy.logging.dto",
                "spring.json.value.default.type", "com.ssafy.logging.dto.TokenUsageEvent",
                "spring.json.use.type.headers", "false"
        );
        return ReceiverOptions.<String,TokenUsageEvent>create(props)
                .subscription(List.of("token-usage"));
    }

    @Bean
    public ReceiverOptions<String, ServerUsageEvent> serverReceiverOptions(
            @Value("${app.kafka.bootstrap-servers}") String brokers,
            @Value("${app.kafka.group-id}") String groupId
    ) {
        Map<String,Object> props = Map.of(
                "bootstrap.servers", brokers,
                "group.id", groupId,
                "key.deserializer", StringDeserializer.class,
                "value.deserializer", JsonDeserializer.class,
                JsonDeserializer.TRUSTED_PACKAGES, "com.ssafy.logging.dto",
                "spring.json.value.default.type", "com.ssafy.logging.dto.ServerUsageEvent",
                "spring.json.use.type.headers", "false"
        );
        return ReceiverOptions.<String,ServerUsageEvent>create(props)
                .subscription(List.of("server-usage"));
    }

    @Bean
    public KafkaReceiver<String,TokenUsageEvent> tokenReceiver(
            ReceiverOptions<String,TokenUsageEvent> opts
    ) {
        return KafkaReceiver.create(opts);
    }

    @Bean
    public KafkaReceiver<String, ServerUsageEvent> serverUsageReceiver(
            ReceiverOptions<String, ServerUsageEvent> opts
    ) {
        return KafkaReceiver.create(opts);
    }

}
