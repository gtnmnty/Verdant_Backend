package com.verdant.salon_ecomm.models.enums.notification;

public enum NotificationType {

    // ── Appointments ─────────────────────────────
    APPOINTMENT_CREATED,
    APPOINTMENT_UPDATED,
    APPOINTMENT_RESCHEDULED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_APPROVED,
    APPOINTMENT_REJECTED,
    APPOINTMENT_REMINDER,
    APPOINTMENT_NO_SHOW,
    STYLIST_ASSIGNED,

    // ── Orders & Payments ────────────────────────
    ORDER_CREATED,
    ORDER_UPDATED,
    ORDER_CANCELLED,
    ORDER_DELETED,
    ORDER_STATUS_CHANGED,
    ORDER_PAYMENT_SUCCESS,
    ORDER_PAYMENT_FAILED,
    ORDER_REFUND_REQUESTED,
    ORDER_REFUNDED,

    // ── Products & Services (catalog) ───────────
    PRODUCT_ADDED,
    PRODUCT_UPDATED,
    PRODUCT_DELETED,
    PRODUCT_LOW_STOCK,
    PRODUCT_OUT_OF_STOCK,
    PRODUCT_BACK_IN_STOCK,
    PRODUCT_PRICE_DROP,
    SERVICE_ADDED,
    SERVICE_UPDATED,
    SERVICE_DELETED,
    SALE_STARTED,
    SALE_ENDING_SOON,

    // ── Branch ───────────────────────────────────
    BRANCH_ADDED,
    BRANCH_UPDATED,
    BRANCH_DELETED,

    // ── Staff / HR ───────────────────────────────
    LEAVE_REQUEST_SUBMITTED,
    LEAVE_REQUEST_APPROVED,
    LEAVE_REQUEST_REJECTED,
    STYLIST_ADDED,
    STYLIST_UPDATED,
    STYLIST_DEACTIVATED,

    // ── Accounts ─────────────────────────────────
    ACCOUNT_CREATED,
    ACCOUNT_UPDATED,
    ACCOUNT_STATUS_CHANGED,
    PASSWORD_CHANGED,
    NEW_LOGIN_DETECTED,

    // ── Reviews ──────────────────────────────────
    REVIEW_RECEIVED,
    REVIEW_REPLIED,

    // ── Promotions & Loyalty ─────────────────────
    PROMO_CODE_ISSUED,
    LOYALTY_POINTS_EARNED,
    LOYALTY_POINTS_EXPIRING,
    MEMBERSHIP_TIER_UPGRADED,

    // ── System / Audit ───────────────────────────
    BULK_ACTION_PERFORMED

}
