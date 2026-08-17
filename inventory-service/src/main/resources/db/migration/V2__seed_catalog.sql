-- A small catalog so the demo has something to sell. SKU-LAST-ONE exists purely
-- to make the overselling race easy to trigger by hand.
INSERT INTO inventory (sku, name, price_cents, available) VALUES
    ('SKU-LAPTOP',   'Aluminium Laptop 14"',      129900, 25),
    ('SKU-PHONE',    'Handset Pro',                74900, 40),
    ('SKU-HEADSET',  'Noise-cancelling Headset',   19900, 60),
    ('SKU-KEYBOARD', 'Mechanical Keyboard',         8900, 15),
    ('SKU-LAST-ONE', 'Limited Edition Mug',         1500,  1)
ON CONFLICT (sku) DO NOTHING;
