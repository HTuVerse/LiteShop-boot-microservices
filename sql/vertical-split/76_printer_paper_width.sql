-- 现有打印机默认按 58mm 安全排版；80mm 设备需在后台改为 80mm。
ALTER TABLE `printer_info`
  ADD COLUMN `paper_width` tinyint NOT NULL DEFAULT 0 COMMENT '打印机实际装纸宽度：0为58mm，1为80mm' AFTER `template_id`;
