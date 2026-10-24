-- Contact details captured on an order placed by staff (audit L-9).
-- Staff-created orders used to overwrite the customer's saved name and phone on users;
-- the details entered for the order now live on the order itself. NULL = use the account's details.
ALTER TABLE orders
    ADD COLUMN contact_name  VARCHAR(100),
    ADD COLUMN contact_phone VARCHAR(20);
