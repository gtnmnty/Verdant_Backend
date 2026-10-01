package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.gitftcards.GiftCardDto;
import com.verdant.salon_ecomm.dtos.gitftcards.GiftCardTransactionDto;
import com.verdant.salon_ecomm.models.entities.GiftCard;
import com.verdant.salon_ecomm.models.entities.GiftCardTransaction;
import org.springframework.stereotype.Component;

@Component
public class GiftCardMapper {

    public GiftCardDto toDto(GiftCard giftCard) {
        return new GiftCardDto(
            giftCard.getId(),
            giftCard.getCode(),
            giftCard.getBalance(),
            giftCard.getInitialAmount(),
            giftCard.getStatus(),
            giftCard.getPaymentStatus(),
            giftCard.getRecipientName(),
            giftCard.getRecipientEmail(),
            giftCard.getNote(),
            giftCard.getExpiresAt(),
            giftCard.getCreatedAt()
        );
    }

    public GiftCardTransactionDto toDto(GiftCardTransaction tx) {
        return new GiftCardTransactionDto(
            tx.getId(),
            tx.getType(),
            tx.getAmount(),
            tx.getDescription(),
            tx.getOrder() != null ? tx.getOrder().getId() : null,
            tx.getCreatedAt()
        );
    }
}