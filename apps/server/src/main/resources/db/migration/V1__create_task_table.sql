CREATE TYPE task_status AS ENUM ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED');

CREATE TABLE task (
    task_id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    task_type VARCHAR(100) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    params JSONB DEFAULT '{}'::jsonb,
    status task_status NOT NULL DEFAULT 'PENDING',
    result_path VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_msg TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT now(),
    update_time TIMESTAMP NOT NULL DEFAULT now()
);
