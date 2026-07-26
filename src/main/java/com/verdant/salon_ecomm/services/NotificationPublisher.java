package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.notification.NotificationResponseDto;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;

@Component
public class NotificationPublisher {

    private record Envelope(UUID recipientUserId, NotificationResponseDto notification) {
    }

    private final Sinks.Many<Envelope> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(UUID recipientUserId, NotificationResponseDto notification) {
        sink.tryEmitNext(new Envelope(recipientUserId, notification));
    }

    public Flux<NotificationResponseDto> streamFor(UUID userId) {
        return sink.asFlux()
                .filter(envelope -> envelope.recipientUserId().equals(userId))
                .map(Envelope::notification);
    }
}
