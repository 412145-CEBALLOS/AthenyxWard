package com.athenyx.backend.payment;

import com.athenyx.backend.entity.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercadoPagoProviderTest {

    @Mock
    private MercadoPagoProperties properties;

    private MercadoPagoProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MercadoPagoProvider(properties);
    }

    @Test
    void getName_returnsMercadoPago() {
        assertThat(provider.getName()).isEqualTo(PaymentProvider.MERCADOPAGO);
    }

    @Test
    void createCheckout_notConfigured_returnsStubSessionWithExternalReference() {
        when(properties.isConfigured()).thenReturn(false);

        CheckoutSession session = provider.createCheckout("order_6_1784669031790",
                new BigDecimal("9.99"), "ARS");

        assertThat(session.providerRef()).startsWith("mp_");
        assertThat(session.externalReference()).isEqualTo("order_6_1784669031790");
        assertThat(session.redirectUrl()).isEqualTo("/checkout/mp-stub");
        assertThat(session.expiresAt()).isNotNull();
    }

    @Test
    void createCheckout_notConfigured_externalReferenceMatchesOrderRef() {
        when(properties.isConfigured()).thenReturn(false);

        String orderRef = "order_" + 1 + "_" + System.currentTimeMillis();
        CheckoutSession session = provider.createCheckout(orderRef, new BigDecimal("9.99"), "ARS");

        assertThat(session.externalReference()).isEqualTo(orderRef);
        assertThat(session.providerRef()).isNotEqualTo(orderRef);
    }
}
