package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.dtos.notification.NotificationGroupDto;
import com.verdant.salon_ecomm.dtos.notification.NotificationResponseDto;
import com.verdant.salon_ecomm.entities.Notification;
import com.verdant.salon_ecomm.entities.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class NotificationMapper {

    public NotificationResponseDto toResponseDto(Notification entity) {
        if (entity == null) {
            return null;
        }
        return new NotificationResponseDto(
            entity.getId(),
            entity.getType(),
            entity.getTitle(),
            entity.getMessage(),
            entity.getReferenceType(),
            entity.getReferenceId(),
            entity.getIsRead(),
            entity.getReadAt(),
            entity.getPriority(),
            entity.getActorId(),
            entity.getActorName(),
            entity.getCreatedAt()
        );
    }

    public List<NotificationResponseDto> toResponseDtoList(List<Notification> entities) {
        return entities.stream()
            .map(this::toResponseDto)
            .collect(Collectors.toList());
    }

    public List<NotificationGroupDto> toGroupedByDate(List<NotificationResponseDto> notifications) {
        Map<LocalDate, List<NotificationResponseDto>> grouped = new LinkedHashMap<>();

        for (NotificationResponseDto dto : notifications) {
            LocalDate date = dto.createdAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
            grouped.computeIfAbsent(date, unused -> new ArrayList<>()).add(dto);
        }

        return grouped.entrySet().stream()
            .map(e -> new NotificationGroupDto(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    public Notification fromCreateDto(NotificationCreateDto dto, User user) {
        return Notification.builder()
            .user(user)
            .type(dto.type())
            .title(dto.title())
            .message(dto.message())
            .referenceType(dto.referenceType())
            .referenceId(dto.referenceId())
            .priority(dto.priority())
            .actorId(dto.actorId())
            .actorName(dto.actorName())
            .build();
    }
}
