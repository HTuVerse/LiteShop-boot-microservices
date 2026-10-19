-- 餐厅库：让模板配置的打印联数随任务持久化，失败重试沿用原始联数。
ALTER TABLE `print_job`
    ADD COLUMN IF NOT EXISTS `print_copies` int NOT NULL DEFAULT 1 COMMENT '该任务打印联数，1到5联' AFTER `content`;
