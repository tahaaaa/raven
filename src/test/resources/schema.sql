CREATE TABLE IF NOT EXISTS email_requests (
  request_id VARCHAR(255) NOT NULL,
  recipient VARCHAR(255) NOT NULL,
  template_id VARCHAR(255) NOT NULL,
  status VARCHAR(255) NOT NULL,
  inject VARCHAR(2048),  -- JSON string
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
  PRIMARY KEY(request_id, recipient)
);
