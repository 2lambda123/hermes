package pl.allegro.tech.hermes.frontend.server

import org.apache.avro.Schema
import pl.allegro.tech.hermes.api.ContentType
import pl.allegro.tech.hermes.api.Topic
import pl.allegro.tech.hermes.frontend.cache.topic.TopicsCache
import pl.allegro.tech.hermes.schema.CompiledSchema
import pl.allegro.tech.hermes.schema.CompiledSchemaRepository
import pl.allegro.tech.hermes.schema.SchemaRepository
import pl.allegro.tech.hermes.schema.SchemaVersion
import pl.allegro.tech.hermes.schema.SchemaVersionsRepository
import pl.allegro.tech.hermes.schema.SchemaVersionsResult
import pl.allegro.tech.hermes.test.helper.avro.AvroUser
import pl.allegro.tech.hermes.test.helper.builder.TopicBuilder
import spock.lang.Shared
import spock.lang.Specification

import static CachedTopicsTestHelper.cachedTopic

class TopicSchemaLoadingStartupHookTest extends Specification {

    @Shared SchemaVersion version = SchemaVersion.valueOf(1)

    @Shared Topic avroTopic1 = avroTopic("g1.topic1")

    @Shared Topic avroTopic2 = avroTopic("g1.topic2")

    @Shared Topic jsonTopic1 = jsonTopic("g1.topic3")

    @Shared Topic avroTopic3 = avroTopic("g2.topic1")

    @Shared CompiledSchema<Schema> schema = new AvroUser().getCompiledSchema()
    
    def "should load topic schema for Avro topics"() {
        given:
        CompiledSchemaRepository<Schema> compiledSchemaRepository = Mock()
        TopicsCache topicsCache = Mock() {
            getTopics() >> [cachedTopic(avroTopic1), cachedTopic(avroTopic2), cachedTopic(jsonTopic1), cachedTopic(avroTopic3)]
        }
        def schemaRepository = new SchemaRepository(schemaVersionsRepositoryForAvroTopics(), compiledSchemaRepository)
        def hook = new TopicSchemaLoadingStartupHook(topicsCache, schemaRepository, 2, 2, true)

        when:
        hook.run()

        then:
        1 * compiledSchemaRepository.getSchema(avroTopic1, version) >> schema
        0 * compiledSchemaRepository.getSchema(jsonTopic1, version) >> schema
        1 * compiledSchemaRepository.getSchema(avroTopic2, version) >> schema
        1 * compiledSchemaRepository.getSchema(avroTopic3, version) >> schema
    }

    def "should retry to load topic schema for Avro topics"() {
        given:
        CompiledSchemaRepository<Schema> compiledSchemaRepository = Mock()
        TopicsCache topicsCache = Mock() {
            getTopics() >> [cachedTopic(avroTopic1), cachedTopic(avroTopic2), cachedTopic(jsonTopic1), cachedTopic(avroTopic3)]
        }
        def schemaRepository = new SchemaRepository(schemaVersionsRepositoryForAvroTopics(), compiledSchemaRepository)
        def hook = new TopicSchemaLoadingStartupHook(topicsCache, schemaRepository, 2, 2, true)

        when:
        hook.run()

        then:
        1 * compiledSchemaRepository.getSchema(avroTopic1, version) >> { throw new RuntimeException("an error") }
        1 * compiledSchemaRepository.getSchema(avroTopic1, version) >> { throw new RuntimeException("an error") }
        1 * compiledSchemaRepository.getSchema(avroTopic1, version) >> schema

        0 * compiledSchemaRepository.getSchema(jsonTopic1, version) >> schema

        1 * compiledSchemaRepository.getSchema(avroTopic2, version) >> { throw new RuntimeException("an error") }
        1 * compiledSchemaRepository.getSchema(avroTopic2, version) >> schema

        1 * compiledSchemaRepository.getSchema(avroTopic3, version) >> schema
    }

    def "should not retry loading schema for topics that have no schema"() {
        given:
        CompiledSchemaRepository<Schema> compiledSchemaRepository = Mock()
        TopicsCache topicsCache = Mock() {
            getTopics() >> [cachedTopic(avroTopic1), cachedTopic(avroTopic3)]
        }
        SchemaVersionsRepository schemaVersionsRepositoryForTopicsWithoutSchema = Mock()
        SchemaVersionsRepository schemaVersionsRepository = [
                versions: { Topic topic ->
                    topic == avroTopic1
                            ? SchemaVersionsResult.succeeded([version])
                            : schemaVersionsRepositoryForTopicsWithoutSchema.versions(topic)
                }
        ] as SchemaVersionsRepository
        def schemaRepository = new SchemaRepository(schemaVersionsRepository, compiledSchemaRepository)
        def hook = new TopicSchemaLoadingStartupHook(topicsCache, schemaRepository, 2, 2, true)

        when:
        hook.run()

        then:
        1 * compiledSchemaRepository.getSchema(avroTopic1, version) >> schema
        0 * compiledSchemaRepository.getSchema(avroTopic3, version) >> schema

        1 * schemaVersionsRepositoryForTopicsWithoutSchema.versions(avroTopic3) >> []
    }

    def "should fail to load after reaching max retries count"() {
        given:
        CompiledSchemaRepository<Schema> compiledSchemaRepository = Mock()
        TopicsCache topicsCache = Mock() {
            getTopics() >> [cachedTopic(avroTopic1)]
        }
        def schemaRepository = new SchemaRepository(schemaVersionsRepositoryForAvroTopics(), compiledSchemaRepository)
        def hook = new TopicSchemaLoadingStartupHook(topicsCache, schemaRepository, 2, 2, true)

        when:
        hook.run()

        then:
        3 * compiledSchemaRepository.getSchema(avroTopic1, version) >> { throw new RuntimeException("an error") }
    }

    def "should not throw exception when no topics exist"() {
        given:
        TopicsCache topicsCache = Mock() {
            getTopics() >> []
        }
        CompiledSchemaRepository<Schema> compiledSchemaRepository = Mock()
        SchemaVersionsRepository schemaVersionsRepository = Mock()
        def schemaRepository = new SchemaRepository(schemaVersionsRepository, compiledSchemaRepository)
        def hook = new TopicSchemaLoadingStartupHook(topicsCache, schemaRepository, 2, 2, true)

        when:
        hook.run()

        then:
        noExceptionThrown()
    }

    private SchemaVersionsRepository schemaVersionsRepositoryForAvroTopics() {
        [
            versions: { Topic topic ->
                topic.contentType == ContentType.AVRO
                        ? SchemaVersionsResult.succeeded([version])
                        : SchemaVersionsResult.succeeded([])
            }
        ] as SchemaVersionsRepository
    }

    private static Topic avroTopic(String name) {
        createTopic(name, ContentType.AVRO)
    }

    private static Topic jsonTopic(String name) {
        createTopic(name, ContentType.JSON)
    }

    private static Topic createTopic(String name, ContentType contentType) {
        TopicBuilder.topic(name).withContentType(contentType).build()
    }
}
