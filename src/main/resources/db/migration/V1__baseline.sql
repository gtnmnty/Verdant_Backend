-- Baseline: the production schema as it stood before V2, taken with
--   pg_dump --schema-only --no-owner --no-acl (from verdant_prod.dump, 2026-10-29).
-- Production already has all of this, so Flyway baselines it at version 1 and never runs this file there
-- (spring.flyway.baseline-version=1). A fresh database (local, Testcontainers) runs it, then V2 onwards.
--
-- Left out on purpose, because they are Supabase-only and the app does not rely on them (it connects as the
-- table owner, which bypasses RLS):
--   - row level security and its policies (they call auth.uid(), which plain Postgres doesn't have)
--   - the rls_auto_enable() event-trigger function
--   - grants/ownership and the pg_dump session settings (one of them empties search_path)

CREATE EXTENSION IF NOT EXISTS btree_gist;


--
-- Name: appointment_time_range(timestamp with time zone, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.appointment_time_range(start_time timestamp with time zone, duration_min integer) RETURNS tstzrange
    LANGUAGE sql IMMUTABLE
    AS $$
  SELECT tstzrange(start_time, start_time + (duration_min || ' minutes')::interval);
$$;

--
-- Name: set_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

--
-- Name: appointment_code_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.appointment_code_seq
    START WITH 3
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: appointments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    stylist_id uuid,
    service_id uuid,
    service_name character varying(200) NOT NULL,
    service_type character varying(20) NOT NULL,
    scheduled_at timestamp with time zone NOT NULL,
    duration_minutes integer NOT NULL,
    home_address jsonb,
    guests smallint DEFAULT 1 NOT NULL,
    notes text,
    status character varying(30) DEFAULT 'pending'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    price_snapshot numeric(10,2) NOT NULL,
    appointment_code character varying(255),
    branch_id uuid,
    updated_at timestamp(6) with time zone,
    CONSTRAINT chk_service_type_location CHECK (((((service_type)::text = 'HOME_SERVICE'::text) AND (home_address IS NOT NULL) AND (branch_id IS NULL)) OR (((service_type)::text = 'IN_SALON'::text) AND (branch_id IS NOT NULL) AND (home_address IS NULL))))
);

--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    entity_id character varying(255),
    entity_type character varying(255) NOT NULL,
    action_type character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    detail character varying(255),
    actor_id uuid,
    actor_label character varying(255),
    self_service boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;

--
-- Name: branch_services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.branch_services (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    branch_id uuid NOT NULL,
    service_id uuid NOT NULL,
    is_available_at_branch boolean DEFAULT true NOT NULL
);

--
-- Name: branches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.branches (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    address_line1 character varying(255),
    address_line2 character varying(255),
    address_city character varying(100),
    address_state character varying(100),
    address_postal character varying(20),
    address_country character varying(5) DEFAULT 'PH'::character varying,
    phone character varying(20),
    operating_hours jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    email character varying(255),
    google_maps_url character varying(255),
    image_url character varying(255),
    status character varying(255) DEFAULT 'OPEN'::character varying NOT NULL,
    CONSTRAINT branches_status_check CHECK (((status)::text = ANY (ARRAY[('OPEN'::character varying)::text, ('CLOSED'::character varying)::text])))
);

--
-- Name: cart_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cart_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    delivery_option character varying(50) DEFAULT 'standard'::character varying NOT NULL,
    added_at timestamp with time zone DEFAULT now(),
    CONSTRAINT chk_quantity CHECK ((quantity > 0))
);

--
-- Name: favorites; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.favorites (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    target_id uuid NOT NULL,
    target_type character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);

--
-- Name: gift_card_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gift_card_transactions (
    id uuid NOT NULL,
    amount numeric(10,2) NOT NULL,
    created_at timestamp(6) with time zone,
    description character varying(255) NOT NULL,
    type character varying(20) NOT NULL,
    gift_card_id uuid NOT NULL,
    order_id uuid,
    CONSTRAINT gift_card_transactions_type_check CHECK (((type)::text = ANY ((ARRAY['PURCHASE'::character varying, 'REDEMPTION'::character varying, 'ORDER_PAYMENT'::character varying, 'REFUND'::character varying, 'ADMIN_ADJUSTMENT'::character varying])::text[])))
);

--
-- Name: gift_cards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gift_cards (
    id uuid NOT NULL,
    balance numeric(10,2) NOT NULL,
    code character varying(30) NOT NULL,
    created_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone,
    initial_amount numeric(10,2) NOT NULL,
    note text,
    payment_status character varying(20) NOT NULL,
    recipient_email character varying(255),
    recipient_name character varying(100),
    status character varying(20) NOT NULL,
    stripe_payment_intent_id character varying(255),
    updated_at timestamp(6) with time zone,
    owner_id uuid,
    purchaser_id uuid,
    CONSTRAINT gift_cards_payment_status_check CHECK (((payment_status)::text = ANY ((ARRAY['PENDING'::character varying, 'REQUIRES_ACTION'::character varying, 'PROCESSED'::character varying, 'PAID'::character varying, 'FAILED'::character varying, 'REFUNDED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT gift_cards_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'REDEEMED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
);

--
-- Name: journal_stories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.journal_stories (
    id uuid NOT NULL,
    client_name character varying(100) NOT NULL,
    quote text NOT NULL,
    category character varying(30) NOT NULL,
    service character varying(200) NOT NULL,
    stylist character varying(100) NOT NULL,
    status character varying(20) NOT NULL,
    result_image_url character varying(500) NOT NULL,
    result_image_public_id character varying(255) NOT NULL,
    result_image_width integer NOT NULL,
    result_image_height integer NOT NULL,
    product_image_url character varying(500),
    product_image_public_id character varying(255),
    product_image_width integer,
    product_image_height integer,
    published_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone
);

--
-- Name: journal_story_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.journal_story_products (
    id uuid NOT NULL,
    story_id uuid NOT NULL,
    product_id uuid NOT NULL,
    sort_order integer NOT NULL
);

--
-- Name: media_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.media_images (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    entity_type character varying(20) NOT NULL,
    entity_id uuid NOT NULL,
    url character varying(500) NOT NULL,
    public_id character varying(255) NOT NULL,
    is_primary boolean DEFAULT false,
    sort_order integer DEFAULT 0,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT media_images_entity_type_check CHECK (((entity_type)::text = ANY ((ARRAY['PRODUCT'::character varying, 'SALON_SERVICE'::character varying])::text[])))
);

--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    type character varying(255) NOT NULL,
    title character varying(150) NOT NULL,
    message character varying(255) NOT NULL,
    is_read boolean DEFAULT false NOT NULL,
    reference_type character varying(50),
    reference_id uuid,
    created_at timestamp with time zone DEFAULT now(),
    actor_id uuid,
    actor_name character varying(150),
    priority character varying(20),
    read_at timestamp(6) with time zone,
    CONSTRAINT notifications_priority_check CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'INFO'::character varying, 'WARNING'::character varying, 'CRITICAL'::character varying])::text[])))
);

--
-- Name: order_code_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.order_code_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    product_id uuid,
    product_name character varying(200) NOT NULL,
    product_image text,
    quantity integer NOT NULL,
    unit_price numeric(10,2) NOT NULL,
    subtotal numeric(10,2) NOT NULL,
    delivery_option character varying(30) NOT NULL,
    CONSTRAINT order_items_delivery_option_check CHECK (((delivery_option)::text = ANY ((ARRAY['STANDARD'::character varying, 'EXPRESS'::character varying, 'SAME_DAY'::character varying])::text[])))
);

--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid,
    status character varying(30) DEFAULT 'pending'::character varying NOT NULL,
    payment_status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    payment_method character varying(50) NOT NULL,
    stripe_payment_intent_id character varying(255),
    snap_address_line1 character varying(255),
    snap_address_line2 character varying(255),
    snap_address_city character varying(100),
    snap_address_state character varying(100),
    snap_address_postal character varying(20),
    snap_address_country character varying(2),
    subtotal numeric(10,2) NOT NULL,
    delivery_fee numeric(10,2) DEFAULT 0.00 NOT NULL,
    total numeric(10,2) NOT NULL,
    notes text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    order_code character varying(255),
    address_city character varying(100),
    address_country character varying(5),
    address_line1 character varying(255),
    address_line2 character varying(255),
    address_postal character varying(20),
    address_state character varying(100),
    wallet_amount_applied numeric(10,2) DEFAULT 0 NOT NULL,
    promo_discount_amount numeric(10,2) DEFAULT 0 NOT NULL
);

--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: pending_cleanup_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pending_cleanup_jobs (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    avatar_public_id character varying(255),
    stripe_customer_id character varying(255),
    processed boolean DEFAULT false NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    claimed_by character varying(255),
    claim_expires_at timestamp with time zone
);

--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(200) NOT NULL,
    category character varying(100) NOT NULL,
    price numeric(10,2) NOT NULL,
    description text,
    info text[] DEFAULT '{}'::text[] NOT NULL,
    badge character varying(50),
    stock_quantity integer DEFAULT 0 NOT NULL,
    review_count integer DEFAULT 0 NOT NULL,
    average_rating numeric(3,2) DEFAULT 0.00 NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    sku character varying(100),
    sale_price numeric(38,2),
    low_stock_threshold integer DEFAULT 10,
    tags jsonb,
    is_featured boolean DEFAULT false,
    updated_at timestamp with time zone DEFAULT now(),
    image_urls text[],
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT products_category_check CHECK (((category)::text = ANY ((ARRAY['SKIN_CARE'::character varying, 'HAIR_CARE'::character varying, 'MAKE_UP'::character varying])::text[]))),
    CONSTRAINT products_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);

--
-- Name: promo_code_redemptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promo_code_redemptions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    promo_code_id uuid NOT NULL,
    user_id uuid NOT NULL,
    order_id uuid NOT NULL,
    discount_amount numeric(10,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: promo_codes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promo_codes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(30) NOT NULL,
    discount_percent numeric(5,2) NOT NULL,
    min_order_amount numeric(10,2),
    max_uses_per_user integer DEFAULT 1 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    description text,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_promo_date_range CHECK (((starts_at IS NULL) OR (ends_at IS NULL) OR (starts_at < ends_at))),
    CONSTRAINT chk_promo_discount_percent CHECK (((discount_percent > (0)::numeric) AND (discount_percent <= (100)::numeric))),
    CONSTRAINT chk_promo_max_uses CHECK ((max_uses_per_user > 0)),
    CONSTRAINT chk_promo_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
);

--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token text NOT NULL,
    jti character varying(255) NOT NULL,
    issued_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked boolean DEFAULT false NOT NULL
);

--
-- Name: reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reviews (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    target_id uuid NOT NULL,
    target_type character varying(20) NOT NULL,
    stars smallint NOT NULL,
    text text,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT chk_stars CHECK (((stars >= 1) AND (stars <= 5))),
    CONSTRAINT reviews_target_type_check CHECK (((target_type)::text = ANY ((ARRAY['PRODUCT'::character varying, 'SERVICE'::character varying])::text[])))
);

--
-- Name: salon_services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.salon_services (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(200) NOT NULL,
    sub_name character varying(255) NOT NULL,
    category character varying(100) NOT NULL,
    duration_minutes integer NOT NULL,
    price numeric(10,2) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    description text,
    images text[] DEFAULT '{}'::text[],
    info text[] DEFAULT '{}'::text[],
    tags text[] DEFAULT '{}'::text[],
    badge character varying(50),
    review_count integer DEFAULT 0 NOT NULL,
    average_rating numeric(3,2) DEFAULT 0 NOT NULL,
    is_home_service boolean DEFAULT false NOT NULL,
    is_featured boolean DEFAULT false,
    created_at timestamp with time zone,
    updated_at timestamp with time zone
);

--
-- Name: stripe_webhook_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stripe_webhook_events (
    id character varying(255) DEFAULT gen_random_uuid() NOT NULL,
    stripe_event_id character varying(100) NOT NULL,
    event_type character varying(100) NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: stylist_services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stylist_services (
    stylist_id uuid NOT NULL,
    service_id uuid NOT NULL
);

--
-- Name: stylists; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stylists (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    email character varying(255),
    phone character varying(20),
    status character varying(20) DEFAULT 'active'::character varying NOT NULL,
    bio text,
    avatar_url text,
    branch_location character varying(100),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    branch_id uuid
);

--
-- Name: support_ticket_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.support_ticket_messages (
    id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    author_id uuid NOT NULL,
    from_staff boolean NOT NULL,
    is_internal boolean NOT NULL,
    body text NOT NULL,
    created_at timestamp with time zone
);

--
-- Name: support_ticket_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.support_ticket_number_seq
    START WITH 2001
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: support_tickets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.support_tickets (
    id uuid NOT NULL,
    ticket_number character varying(20) NOT NULL,
    user_id uuid NOT NULL,
    assigned_to_id uuid,
    subject character varying(200) NOT NULL,
    category character varying(30) NOT NULL,
    status character varying(20) NOT NULL,
    priority character varying(20) NOT NULL,
    contact_name character varying(100) NOT NULL,
    contact_email character varying(254) NOT NULL,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    resolved_at timestamp with time zone
);

--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    full_name character varying(100) NOT NULL,
    email character varying(255) NOT NULL,
    phone character varying(20),
    password_hash character varying(255) NOT NULL,
    role character varying(20) DEFAULT 'CUSTOMER'::character varying NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    stripe_customer_id character varying(255),
    address_line1 character varying(255),
    address_line2 character varying(255),
    address_city character varying(100),
    address_state character varying(100),
    address_postal character varying(20),
    address_country character varying(5) DEFAULT 'PH'::bpchar,
    stripe_pm_id character varying(255),
    display_brand character varying(30),
    display_last4 character varying(4),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    email_verified boolean DEFAULT false,
    email_verified_at timestamp with time zone,
    avatar_url character varying(500),
    avatar_public_id character varying(255),
    verification_code character varying(255),
    "verificationCode_expiration" timestamp with time zone,
    enabled boolean DEFAULT false NOT NULL,
    verification_code_expiration timestamp(6) with time zone,
    reset_password_code character varying(255),
    reset_password_code_expiration timestamp with time zone
);

--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);

--
-- Name: appointments appointments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_pkey PRIMARY KEY (id);

--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);

--
-- Name: branch_services branch_services_branch_id_service_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branch_services
    ADD CONSTRAINT branch_services_branch_id_service_id_key UNIQUE (branch_id, service_id);

--
-- Name: branch_services branch_services_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branch_services
    ADD CONSTRAINT branch_services_pkey PRIMARY KEY (id);

--
-- Name: branches branches_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT branches_name_key UNIQUE (name);

--
-- Name: branches branches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT branches_pkey PRIMARY KEY (id);

--
-- Name: cart_items cart_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT cart_items_pkey PRIMARY KEY (id);

--
-- Name: favorites favorites_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.favorites
    ADD CONSTRAINT favorites_pkey PRIMARY KEY (id);

--
-- Name: gift_card_transactions gift_card_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_card_transactions
    ADD CONSTRAINT gift_card_transactions_pkey PRIMARY KEY (id);

--
-- Name: gift_cards gift_cards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_cards
    ADD CONSTRAINT gift_cards_pkey PRIMARY KEY (id);

--
-- Name: journal_stories journal_stories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.journal_stories
    ADD CONSTRAINT journal_stories_pkey PRIMARY KEY (id);

--
-- Name: journal_story_products journal_story_products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.journal_story_products
    ADD CONSTRAINT journal_story_products_pkey PRIMARY KEY (id);

--
-- Name: media_images media_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_images
    ADD CONSTRAINT media_images_pkey PRIMARY KEY (id);

--
-- Name: appointments no_overlapping_appointments; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT no_overlapping_appointments EXCLUDE USING gist (stylist_id WITH =, public.appointment_time_range(scheduled_at, duration_minutes) WITH &&) WHERE (((status)::text <> 'CANCELLED'::text));

--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);

--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);

--
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);

--
-- Name: password_reset_tokens password_reset_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_token_hash_key UNIQUE (token_hash);

--
-- Name: pending_cleanup_jobs pending_cleanup_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pending_cleanup_jobs
    ADD CONSTRAINT pending_cleanup_jobs_pkey PRIMARY KEY (id);

--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);

--
-- Name: products products_sku_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_sku_key UNIQUE (sku);

--
-- Name: promo_code_redemptions promo_code_redemptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promo_code_redemptions
    ADD CONSTRAINT promo_code_redemptions_pkey PRIMARY KEY (id);

--
-- Name: promo_codes promo_codes_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promo_codes
    ADD CONSTRAINT promo_codes_code_key UNIQUE (code);

--
-- Name: promo_codes promo_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promo_codes
    ADD CONSTRAINT promo_codes_pkey PRIMARY KEY (id);

--
-- Name: refresh_tokens refresh_tokens_jti_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_jti_key UNIQUE (jti);

--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);

--
-- Name: reviews reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_pkey PRIMARY KEY (id);

--
-- Name: reviews reviews_user_target_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_user_target_unique UNIQUE (user_id, target_type, target_id);

--
-- Name: salon_services salon_services_pkey1; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salon_services
    ADD CONSTRAINT salon_services_pkey1 PRIMARY KEY (id);

--
-- Name: stripe_webhook_events stripe_webhook_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stripe_webhook_events
    ADD CONSTRAINT stripe_webhook_events_pkey PRIMARY KEY (id);

--
-- Name: stylist_services stylist_services_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stylist_services
    ADD CONSTRAINT stylist_services_pkey PRIMARY KEY (stylist_id, service_id);

--
-- Name: stylists stylists_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stylists
    ADD CONSTRAINT stylists_email_key UNIQUE (email);

--
-- Name: stylists stylists_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stylists
    ADD CONSTRAINT stylists_pkey PRIMARY KEY (id);

--
-- Name: support_ticket_messages support_ticket_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_ticket_messages
    ADD CONSTRAINT support_ticket_messages_pkey PRIMARY KEY (id);

--
-- Name: support_tickets support_tickets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_pkey PRIMARY KEY (id);

--
-- Name: support_tickets support_tickets_ticket_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_ticket_number_key UNIQUE (ticket_number);

--
-- Name: cart_items uk2beq7cmm05lt6fri8j2w9gh15; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT uk2beq7cmm05lt6fri8j2w9gh15 UNIQUE (user_id, product_id, delivery_option);

--
-- Name: gift_cards ukn2dnvfrs1we40ixtvdtcwnlpu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_cards
    ADD CONSTRAINT ukn2dnvfrs1we40ixtvdtcwnlpu UNIQUE (code);

--
-- Name: appointments uq_appointment_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT uq_appointment_code UNIQUE (appointment_code);

--
-- Name: favorites uq_favorites; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.favorites
    ADD CONSTRAINT uq_favorites UNIQUE (user_id, target_id, target_type);

--
-- Name: favorites uq_favorites_user_target; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.favorites
    ADD CONSTRAINT uq_favorites_user_target UNIQUE (user_id, target_type, target_id);

--
-- Name: journal_story_products uq_journal_story_product; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.journal_story_products
    ADD CONSTRAINT uq_journal_story_product UNIQUE (story_id, product_id);

--
-- Name: reviews uq_reviews_user_target; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT uq_reviews_user_target UNIQUE (user_id, target_type, target_id);

--
-- Name: stripe_webhook_events uq_stripe_event_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stripe_webhook_events
    ADD CONSTRAINT uq_stripe_event_id UNIQUE (stripe_event_id);

--
-- Name: users uq_users_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_email UNIQUE (email);

--
-- Name: stripe_webhook_events uq_webhook_stripe_event_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stripe_webhook_events
    ADD CONSTRAINT uq_webhook_stripe_event_id UNIQUE (stripe_event_id);

--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

--
-- Name: idx_appointments_branch_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointments_branch_id ON public.appointments USING btree (branch_id);

--
-- Name: idx_appointments_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointments_status ON public.appointments USING btree (status);

--
-- Name: idx_appointments_stylist_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointments_stylist_scheduled ON public.appointments USING btree (stylist_id, scheduled_at);

--
-- Name: idx_appointments_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointments_user ON public.appointments USING btree (user_id);

--
-- Name: idx_audit_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_created ON public.audit_logs USING btree (created_at);

--
-- Name: idx_audit_entity_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_entity_created ON public.audit_logs USING btree (entity_type, entity_id, created_at);

--
-- Name: idx_audit_logs_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_created_at ON public.audit_logs USING btree (created_at DESC);

--
-- Name: idx_audit_logs_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_entity ON public.audit_logs USING btree (entity_type, entity_id, created_at DESC);

--
-- Name: idx_branch_services_branch_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_branch_services_branch_id ON public.branch_services USING btree (branch_id);

--
-- Name: idx_cleanup_jobs_unprocessed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cleanup_jobs_unprocessed ON public.pending_cleanup_jobs USING btree (processed);

--
-- Name: idx_journal_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_journal_category ON public.journal_stories USING btree (category);

--
-- Name: idx_journal_status_published; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_journal_status_published ON public.journal_stories USING btree (status, published_at);

--
-- Name: idx_journal_story_products_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_journal_story_products_product ON public.journal_story_products USING btree (product_id);

--
-- Name: idx_media_images_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_media_images_entity ON public.media_images USING btree (entity_type, entity_id);

--
-- Name: idx_notif_user_read_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_user_read_created ON public.notifications USING btree (user_id, is_read, created_at);

--
-- Name: idx_notifications_user_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_user_unread ON public.notifications USING btree (user_id, is_read);

--
-- Name: idx_password_reset_tokens_token_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_password_reset_tokens_token_hash ON public.password_reset_tokens USING btree (token_hash);

--
-- Name: idx_password_reset_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_tokens_user_id ON public.password_reset_tokens USING btree (user_id);

--
-- Name: idx_pending_cleanup_jobs_unprocessed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pending_cleanup_jobs_unprocessed ON public.pending_cleanup_jobs USING btree (processed) WHERE (processed = false);

--
-- Name: idx_promo_codes_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promo_codes_code ON public.promo_codes USING btree (code);

--
-- Name: idx_promo_redemptions_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promo_redemptions_order ON public.promo_code_redemptions USING btree (order_id);

--
-- Name: idx_promo_redemptions_user_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promo_redemptions_user_code ON public.promo_code_redemptions USING btree (promo_code_id, user_id);

--
-- Name: idx_refresh_tokens_jti; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_jti ON public.refresh_tokens USING btree (jti);

--
-- Name: idx_refresh_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_user_id ON public.refresh_tokens USING btree (user_id);

--
-- Name: idx_stripe_webhook_events_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stripe_webhook_events_event_type ON public.stripe_webhook_events USING btree (event_type);

--
-- Name: idx_stylists_branch_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stylists_branch_id ON public.stylists USING btree (branch_id);

--
-- Name: idx_support_msg_ticket_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_support_msg_ticket_created ON public.support_ticket_messages USING btree (ticket_id, created_at);

--
-- Name: idx_support_ticket_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_support_ticket_status_created ON public.support_tickets USING btree (status, created_at);

--
-- Name: idx_support_ticket_user_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_support_ticket_user_created ON public.support_tickets USING btree (user_id, created_at);

--
-- Name: orders trg_orders_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_orders_updated_at BEFORE UPDATE ON public.orders FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

--
-- Name: stylists trg_stylists_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_stylists_updated_at BEFORE UPDATE ON public.stylists FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

--
-- Name: users trg_users_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

--
-- Name: appointments appointments_branch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES public.branches(id);

--
-- Name: audit_logs audit_logs_actor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.users(id);

--
-- Name: branch_services branch_services_branch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branch_services
    ADD CONSTRAINT branch_services_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES public.branches(id) ON DELETE CASCADE;

--
-- Name: branch_services branch_services_service_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branch_services
    ADD CONSTRAINT branch_services_service_id_fkey FOREIGN KEY (service_id) REFERENCES public.salon_services(id) ON DELETE CASCADE;

--
-- Name: gift_cards fk5qkx8h4gq25cjr720es4vyixy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_cards
    ADD CONSTRAINT fk5qkx8h4gq25cjr720es4vyixy FOREIGN KEY (purchaser_id) REFERENCES public.users(id);

--
-- Name: gift_cards fk7svm383xky5mavw8lfev61qgd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_cards
    ADD CONSTRAINT fk7svm383xky5mavw8lfev61qgd FOREIGN KEY (owner_id) REFERENCES public.users(id);

--
-- Name: gift_card_transactions fk9o1y119mydj114oqaym15a92y; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_card_transactions
    ADD CONSTRAINT fk9o1y119mydj114oqaym15a92y FOREIGN KEY (gift_card_id) REFERENCES public.gift_cards(id);

--
-- Name: appointments fk_appointments_service; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fk_appointments_service FOREIGN KEY (service_id) REFERENCES public.salon_services(id) ON DELETE CASCADE;

--
-- Name: appointments fk_appointments_stylist; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fk_appointments_stylist FOREIGN KEY (stylist_id) REFERENCES public.stylists(id) ON DELETE SET NULL;

--
-- Name: appointments fk_appointments_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fk_appointments_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

--
-- Name: cart_items fk_cart_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

--
-- Name: cart_items fk_cart_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

--
-- Name: favorites fk_favorites_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.favorites
    ADD CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

--
-- Name: order_items fk_items_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

--
-- Name: order_items fk_items_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE SET NULL;

--
-- Name: notifications fk_notifications_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

--
-- Name: orders fk_orders_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: reviews fk_reviews_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

--
-- Name: gift_card_transactions fkmsjr43rhccir4geybs7497myw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gift_card_transactions
    ADD CONSTRAINT fkmsjr43rhccir4geybs7497myw FOREIGN KEY (order_id) REFERENCES public.orders(id);

--
-- Name: journal_story_products journal_story_products_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.journal_story_products
    ADD CONSTRAINT journal_story_products_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

--
-- Name: journal_story_products journal_story_products_story_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.journal_story_products
    ADD CONSTRAINT journal_story_products_story_id_fkey FOREIGN KEY (story_id) REFERENCES public.journal_stories(id) ON DELETE CASCADE;

--
-- Name: password_reset_tokens password_reset_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

--
-- Name: promo_code_redemptions promo_code_redemptions_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promo_code_redemptions
    ADD CONSTRAINT promo_code_redemptions_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

--
-- Name: promo_code_redemptions promo_code_redemptions_promo_code_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promo_code_redemptions
    ADD CONSTRAINT promo_code_redemptions_promo_code_id_fkey FOREIGN KEY (promo_code_id) REFERENCES public.promo_codes(id) ON DELETE CASCADE;

--
-- Name: promo_code_redemptions promo_code_redemptions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promo_code_redemptions
    ADD CONSTRAINT promo_code_redemptions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

--
-- Name: promo_codes promo_codes_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promo_codes
    ADD CONSTRAINT promo_codes_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: refresh_tokens refresh_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

--
-- Name: stylist_services stylist_services_service_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stylist_services
    ADD CONSTRAINT stylist_services_service_id_fkey FOREIGN KEY (service_id) REFERENCES public.salon_services(id) ON DELETE CASCADE;

--
-- Name: stylist_services stylist_services_stylist_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stylist_services
    ADD CONSTRAINT stylist_services_stylist_id_fkey FOREIGN KEY (stylist_id) REFERENCES public.stylists(id) ON DELETE CASCADE;

--
-- Name: stylists stylists_branch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stylists
    ADD CONSTRAINT stylists_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES public.branches(id);

--
-- Name: support_ticket_messages support_ticket_messages_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_ticket_messages
    ADD CONSTRAINT support_ticket_messages_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.users(id);

--
-- Name: support_ticket_messages support_ticket_messages_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_ticket_messages
    ADD CONSTRAINT support_ticket_messages_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES public.support_tickets(id) ON DELETE CASCADE;

--
-- Name: support_tickets support_tickets_assigned_to_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_assigned_to_id_fkey FOREIGN KEY (assigned_to_id) REFERENCES public.users(id);

--
-- Name: support_tickets support_tickets_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);

