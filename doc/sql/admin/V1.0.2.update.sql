-- file_save add md5 column
ALTER TABLE `file_web`.`base_file_save` ADD COLUMN `md5` varchar(32) NULL COMMENT '文件MD5' AFTER `attr`;