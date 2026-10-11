package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.support.SupportTicketDetailDto;
import com.verdant.salon_ecomm.dtos.support.SupportTicketMessageDto;
import com.verdant.salon_ecomm.dtos.support.SupportTicketSummaryDto;
import com.verdant.salon_ecomm.dtos.support.SupportTicketUserDto;
import com.verdant.salon_ecomm.dtos.support.admin.AdminSupportTicketSummaryDto;
import com.verdant.salon_ecomm.models.entities.SupportTicket;
import com.verdant.salon_ecomm.models.entities.SupportTicketMessage;
import com.verdant.salon_ecomm.models.entities.User;
import org.springframework.stereotype.Component;

import java.util.List;

 //Must be called inside a transaction (the service's): messages and users are lazy.
 //The staffView flag is the single place that decides what a customer is allowed to see.
@Component
public class SupportTicketMapper {

    public SupportTicketUserDto toUserDto(User user) {
        return user == null ? null : new SupportTicketUserDto(user.getId(), user.getFullName());
    }

    public SupportTicketMessageDto toMessageDto(SupportTicketMessage message) {
        return new SupportTicketMessageDto(
            message.getId(),
            toUserDto(message.getAuthor()),
            message.isFromStaff(),
            message.isInternal(),
            message.getBody(),
            message.getCreatedAt()
        );
    }

    // Customer list row. messageCount must already exclude internal notes.
    public SupportTicketSummaryDto toSummary(SupportTicket ticket, int messageCount) {
        return new SupportTicketSummaryDto(
            ticket.getId(),
            ticket.getTicketNumber(),
            ticket.getSubject(),
            ticket.getCategory(),
            ticket.getStatus(),
            ticket.getPriority(),
            messageCount,
            ticket.getCreatedAt(),
            ticket.getUpdatedAt()
        );
    }

    public AdminSupportTicketSummaryDto toAdminSummary(SupportTicket ticket, int messageCount) {
        return new AdminSupportTicketSummaryDto(
            ticket.getId(),
            ticket.getTicketNumber(),
            ticket.getSubject(),
            ticket.getCategory(),
            ticket.getStatus(),
            ticket.getPriority(),
            toUserDto(ticket.getUser()),
            toUserDto(ticket.getAssignedTo()),
            messageCount,
            ticket.getCreatedAt(),
            ticket.getUpdatedAt()
        );
    }
    
     // @param staffView true  -> includes internal notes and the assignee
     //                  false -> customer view: internal notes dropped, assignee hidden
    public SupportTicketDetailDto toDetail(SupportTicket ticket, boolean staffView) {
        List<SupportTicketMessageDto> messages = ticket.getMessages().stream()
            .filter(m -> staffView || !m.isInternal())
            .map(this::toMessageDto)
            .toList();

        return new SupportTicketDetailDto(
            ticket.getId(),
            ticket.getTicketNumber(),
            ticket.getSubject(),
            ticket.getCategory(),
            ticket.getStatus(),
            ticket.getPriority(),
            ticket.getContactName(),
            ticket.getContactEmail(),
            toUserDto(ticket.getUser()),
            staffView ? toUserDto(ticket.getAssignedTo()) : null,
            messages,
            ticket.getCreatedAt(),
            ticket.getUpdatedAt(),
            ticket.getResolvedAt()
        );
    }
}
