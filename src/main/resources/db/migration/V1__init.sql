-- Ensure table exists
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    stock_quantity INT NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexing for performance
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);

-- Seed Data
INSERT INTO products (id, name, description, price, stock_quantity)
VALUES 
('550e8400-e29b-41d4-a716-446655440000', 'Ergonomic Keyboard', 'Wireless split keyboard', 129.99, 45),
('650e8400-e29b-41d4-a716-446655440001', 'HD Monitor', '27 inch 4K display', 349.50, 12)
ON CONFLICT (id) DO NOTHING;
