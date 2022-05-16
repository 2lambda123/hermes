package pl.allegro.tech.hermes.management.config;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.allegro.tech.hermes.common.message.wrapper.AvroMessageAnySchemaVersionContentWrapper;
import pl.allegro.tech.hermes.common.message.wrapper.AvroMessageContentWrapper;
import pl.allegro.tech.hermes.common.message.wrapper.AvroMessageHeaderSchemaVersionContentWrapper;
import pl.allegro.tech.hermes.common.message.wrapper.AvroMessageHeaderSchemaIdContentWrapper;
import pl.allegro.tech.hermes.common.message.wrapper.AvroMessageSchemaIdAwareContentWrapper;
import pl.allegro.tech.hermes.common.message.wrapper.AvroMessageSchemaVersionTruncationContentWrapper;
import pl.allegro.tech.hermes.common.message.wrapper.DeserializationMetrics;
import pl.allegro.tech.hermes.common.message.wrapper.JsonMessageContentWrapper;
import pl.allegro.tech.hermes.common.message.wrapper.MessageContentWrapper;
import pl.allegro.tech.hermes.schema.SchemaRepository;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(MessageProperties.class)
public class MessageConfiguration {

    @Bean
    MessageContentWrapper messageContentWrapper(MessageProperties messageProperties,
                                                Clock clock,
                                                ObjectMapper objectMapper,
                                                SchemaRepository schemaRepository,
                                                MetricRegistry metricRegistry) {
        DeserializationMetrics metrics = new DeserializationMetrics(metricRegistry);
        AvroMessageContentWrapper avroWrapper = new AvroMessageContentWrapper(clock);
        JsonMessageContentWrapper jsonWrapper = jsonMessageContentWrapper(messageProperties, objectMapper);

        AvroMessageAnySchemaVersionContentWrapper anySchemaWrapper =
                new AvroMessageAnySchemaVersionContentWrapper(schemaRepository, () -> true, avroWrapper, metrics);

        AvroMessageHeaderSchemaVersionContentWrapper headerSchemaVersionWrapper =
                new AvroMessageHeaderSchemaVersionContentWrapper(schemaRepository, avroWrapper, metrics);

        AvroMessageHeaderSchemaIdContentWrapper headerSchemaIdWrapper =
            new AvroMessageHeaderSchemaIdContentWrapper(schemaRepository, avroWrapper, metrics, messageProperties.isSchemaIdHeaderEnabled());

        AvroMessageSchemaIdAwareContentWrapper schemaAwareWrapper =
                new AvroMessageSchemaIdAwareContentWrapper(schemaRepository, avroWrapper, metrics);

        AvroMessageSchemaVersionTruncationContentWrapper schemaVersionTruncationContentWrapper =
                new AvroMessageSchemaVersionTruncationContentWrapper(schemaRepository, avroWrapper, metrics, messageProperties.isSchemaVersionTruncationEnabled());

        return new MessageContentWrapper(
                jsonWrapper,
                avroWrapper,
                schemaAwareWrapper,
                headerSchemaVersionWrapper,
                headerSchemaIdWrapper,
                anySchemaWrapper,
                schemaVersionTruncationContentWrapper);
    }

    private JsonMessageContentWrapper jsonMessageContentWrapper(MessageProperties messageProperties, ObjectMapper objectMapper) {
        return new JsonMessageContentWrapper(messageProperties.getContentRoot(), messageProperties.getMetadataContentRoot(), objectMapper);
    }
}
