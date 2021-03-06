DROP TABLE IF EXISTS jd_product;

CREATE TABLE jd_product
(
  id            INT(11) PRIMARY KEY AUTO_INCREMENT NOT NULL,
  product_id    VARCHAR(16)                        NOT NULL,
  product_name  VARCHAR(16)                        NOT NULL,
  product_price DOUBLE   DEFAULT 0.0,
  create_time   DATETIME DEFAULT CURRENT_TIMESTAMP(),
  update_time   DATETIME
)
  DEFAULT CHARSET UTF8MB4