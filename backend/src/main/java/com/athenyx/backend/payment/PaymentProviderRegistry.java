package com.athenyx.backend.payment;

import com.athenyx.backend.entity.PaymentProvider;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentProviderRegistry {

    private final Map<PaymentProvider, PaymentGatewayProvider> providers;

    public PaymentProviderRegistry(List<PaymentGatewayProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(PaymentGatewayProvider::getName, Function.identity()));
    }

    public Optional<PaymentGatewayProvider> get(PaymentProvider name) {
        return Optional.ofNullable(providers.get(name));
    }

    public Optional<PaymentGatewayProvider> get(String name) {
        try {
            PaymentProvider provider = PaymentProvider.valueOf(name.toUpperCase());
            return get(provider);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public List<PaymentProvider> availableProviders() {
        return List.copyOf(providers.keySet());
    }
}
