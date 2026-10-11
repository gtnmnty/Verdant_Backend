package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.dtos.support.TicketMessageCount;
import com.verdant.salon_ecomm.models.entities.SupportTicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, UUID> {

    // One grouped query for a whole page of tickets instead of one COUNT per ticket.
    @Query("""
            SELECT new com.verdant.salon_ecomm.dtos.support.TicketMessageCount(m.ticket.id, COUNT(m))
            FROM SupportTicketMessage m
            WHERE m.ticket.id IN :ticketIds
              AND (:includeInternal = true OR m.internal = false)
            GROUP BY m.ticket.id
        """)
    List<TicketMessageCount> countByTicketIds(@Param("ticketIds") Collection<UUID> ticketIds,
                                              @Param("includeInternal") boolean includeInternal);
}
