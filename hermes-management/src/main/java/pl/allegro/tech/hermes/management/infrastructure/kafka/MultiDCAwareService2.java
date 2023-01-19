package pl.allegro.tech.hermes.management.infrastructure.kafka;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.allegro.tech.hermes.api.ConsumerGroup;
import pl.allegro.tech.hermes.api.Subscription;
import pl.allegro.tech.hermes.api.SubscriptionName;
import pl.allegro.tech.hermes.api.Topic;
import pl.allegro.tech.hermes.common.exception.InternalProcessingException;
import pl.allegro.tech.hermes.management.domain.auth.RequestUser;
import pl.allegro.tech.hermes.management.domain.dc.MultiDatacenterRepositoryCommandExecutor;
import pl.allegro.tech.hermes.management.domain.retransmit.RetransmitCommand;
import pl.allegro.tech.hermes.management.domain.topic.BrokerTopicManagement;
import pl.allegro.tech.hermes.management.domain.topic.UnableToMoveOffsetsException;
import pl.allegro.tech.hermes.management.infrastructure.kafka.service.BrokersClusterService;

import static java.util.stream.Collectors.toList;

public class MultiDCAwareService2 {

    private static final Logger logger = LoggerFactory.getLogger(MultiDCAwareService2.class);

    private final ClustersProvider2 clustersProvider;
    private final Clock clock;
    private final Duration intervalBetweenCheckingIfOffsetsMoved;
    private final Duration offsetsMovedTimeout;
    private final MultiDatacenterRepositoryCommandExecutor multiDcExecutor;

    public MultiDCAwareService2(ClustersProvider2 clustersProvider, Clock clock,
                                Duration intervalBetweenCheckingIfOffsetsMoved, Duration offsetsMovedTimeout,
                                MultiDatacenterRepositoryCommandExecutor multiDcExecutor) {
        this.clustersProvider = clustersProvider;
        this.clock = clock;
        this.intervalBetweenCheckingIfOffsetsMoved = intervalBetweenCheckingIfOffsetsMoved;
        this.offsetsMovedTimeout = offsetsMovedTimeout;
        this.multiDcExecutor = multiDcExecutor;
    }

    public void manageTopic(Consumer<BrokerTopicManagement> manageFunction) {
        clustersProvider.getClusters().forEach(kafkaService -> kafkaService.manageTopic(manageFunction));
    }

    public String readMessageFromPrimary(String clusterName, Topic topic, Integer partition, Long offset) {
        return clustersProvider.getClusters().stream()
                .filter(cluster -> clusterName.equals(cluster.getClusterName()))
                .findFirst()
                .orElseThrow(() -> new BrokersClusterNotFoundException(clusterName))
                .readMessageFromPrimary(topic, partition, offset);
    }

    public MultiDCOffsetChangeSummary moveOffset(Topic topic,
                                                 String subscriptionName,
                                                 Long timestamp,
                                                 boolean dryRun,
                                                 RequestUser requester) {
        MultiDCOffsetChangeSummary multiDCOffsetChangeSummary = new MultiDCOffsetChangeSummary();

        clustersProvider.getClusters().forEach(cluster -> multiDCOffsetChangeSummary.addPartitionOffsetList(
                cluster.getClusterName(),
                cluster.indicateOffsetChange(topic, subscriptionName, timestamp, dryRun)));

        if (!dryRun) {
            logger.info("Starting retransmission for subscription {}. Requested by {}. Retransmission timestamp: {}",
                    topic.getQualifiedName() + "$" + subscriptionName, requester.getUsername(), timestamp);
            multiDcExecutor.executeByUser(new RetransmitCommand(new SubscriptionName(subscriptionName, topic.getName())), requester);
            clustersProvider.getClusters().forEach(clusters -> waitUntilOffsetsAreMoved(topic, subscriptionName));
            logger.info(
                    "Successfully moved offsets for retransmission of subscription {}. Requested by user: {}. Retransmission timestamp: {}",
                    topic.getQualifiedName() + "$" + subscriptionName, requester.getUsername(), timestamp);
        }

        return multiDCOffsetChangeSummary;
    }

    public boolean areOffsetsAvailableOnAllKafkaTopics(Topic topic) {
        return clustersProvider.getClusters().stream().allMatch(cluster -> cluster.areOffsetsAvailableOnAllKafkaTopics(topic));
    }

    public boolean topicExists(Topic topic) {
        return clustersProvider.getClusters().stream().allMatch(brokersClusterService -> brokersClusterService.topicExists(topic));
    }

    public Set<String> listTopicFromAllDC() {
        return clustersProvider.getClusters().stream()
                .map(BrokersClusterService::listTopicsFromCluster)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public void removeTopicByName(String topicName) {
        clustersProvider.getClusters().forEach(brokersClusterService -> brokersClusterService.removeTopicByName(topicName));
    }

    public void createConsumerGroups(Topic topic, Subscription subscription) {
        clustersProvider.getClusters().forEach(clusterService -> clusterService.createConsumerGroup(topic, subscription));
    }

    private void waitUntilOffsetsAreMoved(Topic topic, String subscriptionName) {
        Instant abortAttemptsInstant = clock.instant().plus(offsetsMovedTimeout);

        while (!areOffsetsMoved(topic, subscriptionName)) {
            if (clock.instant().isAfter(abortAttemptsInstant)) {
                logger.error("Not all offsets related to hermes subscription {}${} were moved.", topic.getQualifiedName(),
                        subscriptionName);
                throw new UnableToMoveOffsetsException(topic, subscriptionName);
            }
            logger.debug("Not all offsets related to hermes subscription {} were moved, will retry", topic.getQualifiedName());

            sleep(intervalBetweenCheckingIfOffsetsMoved);
        }
    }

    private boolean areOffsetsMoved(Topic topic, String subscriptionName) {
        return clustersProvider.getClusters().stream()
                .allMatch(cluster -> cluster.areOffsetsMoved(topic, subscriptionName));
    }

    private void sleep(Duration sleepDuration) {
        try {
            Thread.sleep(sleepDuration.toMillis());
        } catch (InterruptedException e) {
            throw new InternalProcessingException(e);
        }
    }

    public boolean allSubscriptionsHaveConsumersAssigned(Topic topic, List<Subscription> subscriptions) {
        return clustersProvider.getClusters().stream().allMatch(brokersClusterService ->
                brokersClusterService.allSubscriptionsHaveConsumersAssigned(topic, subscriptions));
    }

    public List<ConsumerGroup> describeConsumerGroups(Topic topic, String subscriptionName) {
        return clustersProvider.getClusters().stream().map(brokersClusterService -> brokersClusterService.describeConsumerGroup(topic, subscriptionName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());
    }
}
