-- The help-center and newsletter entities (HelpTopic, HelpArticle, NewsletterSubscriber, NewsletterCampaign,
-- NewsletterDelivery) had no migration and no table in the production dump, so schema validation
-- (ddl-auto=validate) failed on a database built from the migrations. Everything is guarded with IF NOT EXISTS
-- in case a copy of the database already has the tables from an earlier hand-made change.

CREATE TABLE IF NOT EXISTS help_topics (
    id          uuid         NOT NULL PRIMARY KEY,
    slug        varchar(120) NOT NULL UNIQUE,
    title       varchar(150) NOT NULL,
    summary     varchar(500) NOT NULL,
    keywords    varchar(300) NOT NULL DEFAULT '',
    icon        varchar(20)  NOT NULL DEFAULT 'HELP_CIRCLE',
    layout      varchar(20)  NOT NULL DEFAULT 'STANDARD',
    published   boolean      NOT NULL DEFAULT false,
    sort_order  integer      NOT NULL,
    created_at  timestamptz,
    updated_at  timestamptz
);

CREATE INDEX IF NOT EXISTS idx_help_topic_published_order ON help_topics (published, sort_order);

CREATE TABLE IF NOT EXISTS help_articles (
    id          uuid         NOT NULL PRIMARY KEY,
    topic_id    uuid         NOT NULL REFERENCES help_topics (id) ON DELETE CASCADE,
    title       varchar(150) NOT NULL,
    body        text         NOT NULL,
    sort_order  integer      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_help_articles_topic_id ON help_articles (topic_id);

CREATE TABLE IF NOT EXISTS newsletter_subscribers (
    id                 uuid         NOT NULL PRIMARY KEY,
    email              varchar(254) NOT NULL UNIQUE,
    status             varchar(20)  NOT NULL DEFAULT 'SUBSCRIBED',
    source             varchar(20),
    unsubscribe_token  varchar(64)  NOT NULL UNIQUE,
    subscribed_at      timestamptz,
    unsubscribed_at    timestamptz,
    created_at         timestamptz,
    updated_at         timestamptz
);

CREATE INDEX IF NOT EXISTS idx_newsletter_status_created ON newsletter_subscribers (status, created_at);

CREATE TABLE IF NOT EXISTS newsletter_campaigns (
    id               uuid         NOT NULL PRIMARY KEY,
    subject          varchar(200) NOT NULL,
    body             text         NOT NULL,
    cta_label        varchar(60),
    cta_url          varchar(500),
    status           varchar(20)  NOT NULL DEFAULT 'DRAFT',
    created_by_id    uuid         REFERENCES users (id) ON DELETE SET NULL,
    recipient_count  integer,
    started_at       timestamptz,
    completed_at     timestamptz,
    created_at       timestamptz,
    updated_at       timestamptz
);

CREATE INDEX IF NOT EXISTS idx_newsletter_campaign_created ON newsletter_campaigns (created_at);

CREATE TABLE IF NOT EXISTS newsletter_deliveries (
    id             uuid         NOT NULL PRIMARY KEY,
    campaign_id    uuid         NOT NULL REFERENCES newsletter_campaigns (id) ON DELETE CASCADE,
    subscriber_id  uuid         NOT NULL REFERENCES newsletter_subscribers (id) ON DELETE CASCADE,
    status         varchar(20)  NOT NULL,
    error          varchar(500),
    created_at     timestamptz,
    CONSTRAINT uq_newsletter_delivery UNIQUE (campaign_id, subscriber_id)
);
