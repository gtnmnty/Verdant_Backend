package com.verdant.salon_ecomm.services;


import com.verdant.salon_ecomm.dtos.notification.*;
import com.verdant.salon_ecomm.entities.Notification;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.NotificationMapper;
import com.verdant.salon_ecomm.models.enums.notification.NotificationReadFilter;
import com.verdant.salon_ecomm.models.enums.notification.NotificationSortField;
import com.verdant.salon_ecomm.models.enums.notification.SortDirection;
import com.verdant.salon_ecomm.repositories.NotificationRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.NotificationSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.verdant.salon_ecomm.models.enums.notification.NotificationSortField.*;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final NotificationReadFilter DEFAULT_READ_FILTER = NotificationReadFilter.ALL;
    private static final NotificationSortField DEFAULT_SORT_FIELD = CREATED_AT;
    private static final SortDirection DEFAULT_SORT_DIRECTION = SortDirection.DESC;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public NotificationPageDto getNotifications(UUID userId, NotificationQueryDto query) {
        NotificationQueryDto resolved = applyDefaults(query);
        Pageable pageable = buildPageable(resolved);

        Page<Notification> result = notificationRepository.findAll(
            NotificationSpec.forUserWithFilters(userId, resolved),
            pageable
        );

        List<NotificationResponseDto> content = notificationMapper.toResponseDtoList(result.getContent());
        long unreadCount = notificationRepository.countByUser_IdAndIsReadFalse(userId);

        return new NotificationPageDto(
            content,
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            unreadCount
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationGroupDto> getNotificationsGroupedByDate(UUID userId, NotificationQueryDto query) {
        NotificationQueryDto resolved = applyDefaults(query);
        Pageable pageable = buildPageable(resolved);

        Page<Notification> result = notificationRepository.findAll(
            NotificationSpec.forUserWithFilters(userId, resolved),
            pageable
        );

        List<NotificationResponseDto> content = notificationMapper.toResponseDtoList(result.getContent());
        return notificationMapper.toGroupedByDate(content);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUser_IdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponseDto create(NotificationCreateDto request) {
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userId()));

        Notification entity = notificationMapper.fromCreateDto(request, user);
        Notification saved = notificationRepository.save(entity);
        return notificationMapper.toResponseDto(saved);
    }

    @Transactional
    public List<UUID> markAsRead(UUID userId, List<UUID> ids) {
        List<UUID> ownedIds = notificationRepository.findByIdInAndUser_Id(ids, userId).stream()
            .map(Notification::getId)
            .toList();
        if (!ownedIds.isEmpty()) {
            notificationRepository.markAsReadByIdsAndUser(ownedIds, userId, OffsetDateTime.now());
        }
        return ownedIds;
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsReadByUser(userId, OffsetDateTime.now());
    }

    @Transactional
    public List<UUID> delete(UUID userId, List<UUID> ids) {
        List<UUID> ownedIds = notificationRepository.findByIdInAndUser_Id(ids, userId).stream()
            .map(Notification::getId)
            .toList();
        if (!ownedIds.isEmpty()) {
            notificationRepository.deleteByIdInAndUser_Id(ownedIds, userId);
        }
        return ownedIds;
    }

    // ── Helpers ──────────────────────────────────────────

    private NotificationQueryDto applyDefaults(NotificationQueryDto query) {
        if (query == null) {
            return new NotificationQueryDto(
                DEFAULT_READ_FILTER, null, null, DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION,
                DEFAULT_PAGE, DEFAULT_SIZE
            );
        }
        return new NotificationQueryDto(
            query.readFilter() != null ? query.readFilter() : DEFAULT_READ_FILTER,
            query.search(),
            query.types(),
            query.sortField() != null ? query.sortField() : DEFAULT_SORT_FIELD,
            query.sortDirection() != null ? query.sortDirection() : DEFAULT_SORT_DIRECTION,
            query.page() != null ? query.page() : DEFAULT_PAGE,
            query.size() != null ? query.size() : DEFAULT_SIZE
        );
    }

    private Pageable buildPageable(NotificationQueryDto query) {
        int normalizedPage = Math.max(0, query.page());
        int normalizedSize = Math.clamp(query.size(), 1, 100);

        Sort.Direction direction = query.sortDirection() == SortDirection.ASC
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        String property = switch (query.sortField()) {
            case CREATED_AT -> "createdAt";
            case PRIORITY -> "priority";
            case TYPE -> "type";
            case IS_READ -> "isRead";
        };

        return PageRequest.of(normalizedPage, normalizedSize, Sort.by(direction, property));
    }
}