package pl.allegro.tech.hermes.integration;

import jakarta.ws.rs.core.Response;
import org.testng.annotations.Test;
import pl.allegro.tech.hermes.api.EndpointAddress;
import pl.allegro.tech.hermes.api.Subscription;
import pl.allegro.tech.hermes.api.Topic;
import pl.allegro.tech.hermes.test.helper.message.TestMessage;

import static jakarta.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.allegro.tech.hermes.api.SubscriptionPolicy.Builder.subscriptionPolicy;
import static pl.allegro.tech.hermes.test.helper.builder.SubscriptionBuilder.subscription;
import static pl.allegro.tech.hermes.test.helper.builder.TopicBuilder.randomTopic;

public class UndeliveredLogTest extends IntegrationTest {

    private static final String INVALID_ENDPOINT_URL = "http://localhost:60000";

    @Test
    public void shouldLogUndeliveredMessage() {
        // given
        Topic topic = operations.buildTopic(randomTopic("logUndelivered", "topic").build());
        Subscription subscription = subscription(topic, "subscription")
                .withEndpoint(EndpointAddress.of(INVALID_ENDPOINT_URL))
                .withSubscriptionPolicy(subscriptionPolicy().withRate(1).withMessageTtl(0).build())
                .build();

        operations.createSubscription(topic, subscription);

        // when
        publisher.publish(topic.getQualifiedName(), TestMessage.simple().body());

        // then
        wait.until(() -> {
            Response response = management.subscription().getLatestUndeliveredMessage(topic.getQualifiedName(), "subscription");
            assertThat(response.getStatusInfo().getFamily()).isEqualTo(SUCCESSFUL);
        });
    }

}
