package pl.allegro.tech.hermes.management.domain.endpoint;

import com.damnhandy.uri.template.UriTemplate;
import pl.allegro.tech.hermes.api.EndpointAddress;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class EndpointAddressValidator {
    private final AdditionalEndpointAddressValidator additionalEndpointAddressValidator;
    private final Set<String> availableProtocol = new HashSet<>();

    public EndpointAddressValidator(List<String> additionalEndpointProtocols,
                                    AdditionalEndpointAddressValidator additionalEndpointAddressValidator) {
        this.additionalEndpointAddressValidator = additionalEndpointAddressValidator;
        this.availableProtocol.addAll(additionalEndpointProtocols);
        this.availableProtocol.add("http");
        this.availableProtocol.add("https");
        this.availableProtocol.add("jms");
        this.availableProtocol.add("googlepubsub");
    }

    public void check(EndpointAddress address) {
        checkIfProtocolIsValid(address);
        checkIfUriIsValid(address);
        additionalEndpointAddressValidator.check(address);
    }

    private void checkIfProtocolIsValid(EndpointAddress address) {
        if (!availableProtocol.contains(address.getProtocol())) {
            throw new EndpointValidationException("Endpoint address has invalid format");
        }
    }

    private void checkIfUriIsValid(EndpointAddress address) {
        UriTemplate template = UriTemplate.fromTemplate(address.getRawEndpoint());
        if (isInvalidHost(template)) {
            throw new EndpointValidationException("Endpoint contains invalid chars in host name. Underscore is one of them.");
        }
    }

    private boolean isInvalidHost(UriTemplate template) {
        Map<String, Object> uriKeysWithEmptyValues = Arrays.stream(template.getVariables()).collect(toMap(identity(), v -> "empty"));

        //check if host is null due to bug in jdk https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6587184
        return URI.create(template.expand(uriKeysWithEmptyValues)).getHost() == null;
    }
}
