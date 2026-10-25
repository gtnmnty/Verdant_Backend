package com.verdant.salon_ecomm.services;


import com.verdant.salon_ecomm.dtos.notification.*;
import com.verdant.salon_ecomm.models.entities.Notification;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.NotificationMapper;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationReadFilter;
import com.verdant.salon_ecomm.models.enums.notification.NotificationSortField;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.utils.PageRequests;
import com.verdant.salon_ecomm.utils.TextLimits;
import com.verdant.salon_ecomm.models.enums.notification.SortDirection;
import com.verdant.salon_ecomm.repositories.NotificationRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.NotificationSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.verdant.salon_ecomm.models.enums.notification.NotificationSortField.*;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int DEFAULT_SIZE = 20;
    private static final NotificationReadFilter DEFAULT_READ_FILTER = NotificationReadFilter.ALL;
    private static final NotificationSortField DEFAULT_SORT_FIELD = CREATED_AT;
    private static final SortDirection DEFAULT_SORT_DIRECTION = SortDirection.DESC;

    static final int TITLE_MAX = 150;
    static final int MESSAGE_MAX = 1000;
    private static final int ACTOR_NAME_MAX = 150;

    // M-31: the types staff may send by hand. Kept to messages a person would really write (a reminder, an
    // update about a booking or order, a reply to a review). Security and payment types are system-only.
    private static final Set<NotificationType> STAFF_MESSAGE_TYPES = EnumSet.of(
        NotificationType.APPOINTMENT_REMINDER,
        NotificationType.APPOINTMENT_UPDATED,
        NotificationType.ORDER_UPDATED,
        NotificationType.REVIEW_REPLIED
    );

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public NotificationPageDto getNotifications(UUID userId, NotificationQueryDto query) {
        NotificationQueryDto resolved = applyDefaults(query);
        int pageSize = PageRequests.size(resolved.size(), PageRequests.DEFAULT_MAX_SIZE);
        Pageable pageable = buildPageable(resolved);

        Specification<Notification> spec = NotificationSpec.forUserWithFilters(userId, resolved)
            .and(keysetPredicate(resolved));

        Page<Notification> raw = notificationRepository.findAll(spec, pageable);

        List<Notification> rows = raw.getContent();
        boolean hasNextPage = rows.size() > pageSize;
        List<Notification> pageRows = hasNextPage ? rows.subList(0, pageSize) : rows;

        List<NotificationResponseDto> content = notificationMapper.toResponseDtoList(pageRows);
        long unreadCount = notificationRepository.countByUser_IdAndIsReadFalse(userId);

        return new NotificationPageDto(content, hasNextPage, unreadCount);
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(UUID userId) {
        return notificationRepository.countByUser_IdAndIsReadFalse(userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResponseDto create(NotificationCreateDto request) {
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userId()));

        Notification entity = notificationMapper.fromCreateDto(withinColumnLimits(request), user);
        Notification saved = notificationRepository.save(entity);
        return notificationMapper.toResponseDto(saved);
    }

    // M-38: one notification per recipient, written in ONE transaction with one saveAll. StaffNotifier used
    // to call create() once per staff member: a separate REQUIRES_NEW transaction (and a user SELECT) each,
    // all on the request thread. template.userId() is ignored; each copy gets one of userIds.
    // getReferenceById: the rows only need the user's id as a foreign key, so no user is loaded.
    // All or nothing: if one row fails, StaffNotifier falls back to create() per recipient.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createForUsers(Collection<UUID> userIds, NotificationCreateDto template) {
        if (userIds == null || userIds.isEmpty()) return;
        NotificationCreateDto limited = withinColumnLimits(template);
        List<Notification> rows = userIds.stream()
            .map(userId -> notificationMapper.fromCreateDto(limited, userRepository.getReferenceById(userId)))
            .toList();
        notificationRepository.saveAll(rows);
    }

    // M-31: the only way staff send a hand-written notification (createNotification mutation).
    // Flow: type must be on the staff-message allow-list -> title and message required and within limits
    // -> recipient must exist -> create(). Before, any staff member, receptionists included, could send ANY
    // type (PASSWORD_CHANGED, ORDER_REFUNDED...) with unlimited free text to any user: a phishing tool that
    // looked like a real system alert. Too-long text is refused here rather than cut, because a person typed it.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResponseDto createStaffMessage(NotificationCreateDto request) {
        if (request.type() == null || !STAFF_MESSAGE_TYPES.contains(request.type())) {
            throw BusinessRuleException.invalid("Staff can only send these notification types: " + STAFF_MESSAGE_TYPES + ".");
        }
        String title = request.title() == null ? "" : request.title().trim();
        String message = request.message() == null ? "" : request.message().trim();
        if (title.isEmpty() || message.isEmpty()) {
            throw BusinessRuleException.invalid("A notification needs a title and a message.");
        }
        if (title.length() > TITLE_MAX || message.length() > MESSAGE_MAX) {
            throw BusinessRuleException.invalid("Title can be at most " + TITLE_MAX
                + " characters and the message at most " + MESSAGE_MAX + ".");
        }
        // CRITICAL/HIGH are kept for system alerts, so a hand-written message can't look like one.
        NotificationPriority priority = request.priority() == NotificationPriority.WARNING
            ? NotificationPriority.WARNING : NotificationPriority.INFO;
        return create(new NotificationCreateDto(request.userId(), request.type(), title, message,
            request.referenceType(), request.referenceId(), priority, request.actorId(), request.actorName()));
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

    // M-33: listeners build titles and messages from user input (appointment notes, review text) with no
    // length limit. An over-long value used to fail the INSERT, and the listener swallowed the error, so the
    // notification was lost. Now it is cut to fit (with "...") instead. Limits match the columns:
    // title varchar(150), message text since V11 (capped so a notification stays a notification),
    // actor_name varchar(150).
    private NotificationCreateDto withinColumnLimits(NotificationCreateDto dto) {
        return new NotificationCreateDto(
            dto.userId(), dto.type(),
            TextLimits.truncate(dto.title(), TITLE_MAX),
            TextLimits.truncate(dto.message(), MESSAGE_MAX),
            dto.referenceType(), dto.referenceId(), dto.priority(), dto.actorId(),
            TextLimits.truncate(dto.actorName(), ACTOR_NAME_MAX)
        );
    }

    private NotificationQueryDto applyDefaults(NotificationQueryDto query) {
        if (query == null) {
            return new NotificationQueryDto(
                DEFAULT_READ_FILTER, null, null, DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION,
                null, null, DEFAULT_SIZE
            );
        }
        return new NotificationQueryDto(
            query.readFilter() != null ? query.readFilter() : DEFAULT_READ_FILTER,
            query.search(),
            query.types(),
            query.sortField() != null ? query.sortField() : DEFAULT_SORT_FIELD,
            query.sortDirection() != null ? query.sortDirection() : DEFAULT_SORT_DIRECTION,
            query.cursorCreatedAt(),
            query.cursorId(),
            query.size() != null ? query.size() : DEFAULT_SIZE
        );
    }

    private Pageable buildPageable(NotificationQueryDto query) {
        int normalizedSize = PageRequests.size(query.size(), PageRequests.DEFAULT_MAX_SIZE);

        Sort.Direction direction = query.sortDirection() == SortDirection.ASC
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        String property = switch (query.sortField()) {
            case CREATED_AT -> "createdAt";
            // TODO: PRIORITY / TYPE / IS_READ aren't wired for keyset pagination yet —
            // the resolver never requests them today, but if that changes, each needs
            // its own cursor field + tiebreak predicate below, same as createdAt.
            case PRIORITY -> "priority";
            case TYPE -> "type";
            case IS_READ -> "isRead";
        };

        // Fetch one extra row to detect hasNextPage. findAll(spec, pageable) still runs a COUNT
        // for the Page it returns; only the row content is used here.
        return PageRequest.of(0, normalizedSize + 1,
            Sort.by(direction, property).and(Sort.by(direction, "id")));
    }

    private Specification<Notification> keysetPredicate(NotificationQueryDto query) {
        return (root, cq, cb) -> {
            if (query.cursorCreatedAt() == null || query.cursorId() == null) {
                return cb.conjunction(); // first page — no cursor filter
            }
            boolean desc = query.sortDirection() != SortDirection.ASC;

            // (createdAt < cursor) OR (createdAt = cursor AND id < cursorId)   [DESC]
            // (createdAt > cursor) OR (createdAt = cursor AND id > cursorId)  [ASC]
            var createdAtPath = root.<OffsetDateTime>get("createdAt");
            var idPath = root.<UUID>get("id");

            var strictCompare = desc
                ? cb.lessThan(createdAtPath, query.cursorCreatedAt())
                : cb.greaterThan(createdAtPath, query.cursorCreatedAt());

            var tieCompare = cb.and(
                cb.equal(createdAtPath, query.cursorCreatedAt()),
                desc ? cb.lessThan(idPath, query.cursorId()) : cb.greaterThan(idPath, query.cursorId())
            );

            return cb.or(strictCompare, tieCompare);
        };
    }
}