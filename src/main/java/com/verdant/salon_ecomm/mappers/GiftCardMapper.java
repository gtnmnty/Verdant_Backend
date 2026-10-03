package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.gitftcards.GiftCardDto;
import com.verdant.salon_ecomm.dtos.gitftcards.GiftCardTransactionDto;
import com.verdant.salon_ecomm.models.entities.giftcards.GiftCard;
import com.verdant.salon_ecomm.models.entities.giftcards.GiftCardTransaction;
import org.springframework.stereotype.Component;

@Component
public class GiftCardMapper {

    public GiftCardDto toDto(GiftCard giftCard) {
        return build(giftCard, giftCard.getCode());
    }

    // For the purchaser's "sent" list -
    // the code stays secret between the buyer's screen and the recipient.
    public GiftCardDto toMaskedDto(GiftCard giftCard) {
        return build(giftCard, giftCard.maskedCode());
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

    private GiftCardDto build(GiftCard giftCard, String code) {
        return new GiftCardDto(
            giftCard.getId(),
            code,
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
}