-- 创建数据库
CREATE DATABASE IF NOT EXISTS cow;

-- 使用数据库
USE cow;

-- 创建表
CREATE TABLE IF NOT EXISTS cattle_info (
    standard_id VARCHAR(50) PRIMARY KEY,  -- 标准牛号 (设置为主键，确保唯一性)
    birth_date DATE,                      -- 出生日期
    sire_id VARCHAR(50),                  -- 父号
    dam_id VARCHAR(50),                   -- 母号
    maternal_grandsire_id VARCHAR(50),    -- 外祖父号
    maternal_granddam_id VARCHAR(50),     -- 外祖母号
    maternal_granddam_sire_id VARCHAR(50) NULL -- 外祖母父号
);

-- 展示表结构
DESCRIBE cattle_info;


mysql --local-infile=1 -u root -p root
SHOW GLOBAL VARIABLES LIKE 'local_infile';
SET GLOBAL local_infile = 1;

-- *** CSV 文件的实际绝对路径！***
-- LOAD DATA LOCAL INFILE '/path/to/your/csv/file.csv'
-- 指定要导入的目标表
-- 指定 CSV 文件的字符编码 (utf8mb4 通常兼容性好)
-- 指定列与列之间的分隔符是逗号
-- 如果字段值本身包含逗号，通常会被双引号包起来
-- 指定行与行之间的分隔符 (Linux/macOS 通常是 '\n', Windows 可能是 '\r\n')
-- 忽略 CSV 文件中的第一行（表头）
-- 将 CSV 列按顺序映射到临时变量或表列
-- 处理空值：如果CSV里外祖母父号是空字符串，则在数据库中插入 NULL
LOAD DATA LOCAL INFILE 'D:/Downloads/导入测试数据/导入测试数据/母牛系谱导入模板.CSV'
INTO TABLE cattle_info                             
CHARACTER SET utf8mb4                              
FIELDS TERMINATED BY ','                           
OPTIONALLY ENCLOSED BY '"'                         
LINES TERMINATED BY '\n'                           
IGNORE 1 ROWS                                      
(standard_id, birth_date, sire_id, dam_id, maternal_grandsire_id, maternal_granddam_id, @maternal_granddam_sire_id) 
SET maternal_granddam_sire_id = NULLIF(@maternal_granddam_sire_id, ''); 

-- inbreeding_coefficient
ALTER TABLE cattle_info
ADD COLUMN inbreeding_coefficient DECIMAL(9, 6);

ALTER TABLE cattle_info
DROP COLUMN inbreeding_coefficient;

SELECT * FROM cattle_info;



-- Numbering Comparison Table
CREATE TABLE IF NOT EXISTS num_comp_tb (
    ear_num VARCHAR(50), -- 耳号
    id VARCHAR(50), -- 母牛编号
    standard_id VARCHAR(50)  -- 标准牛号
    
);

-- 导入数据
LOAD DATA local infile 'D:/Downloads/导入测试数据/导入测试数据/牛号对应导入模板.CSV'
INTO TABLE num_comp_tb
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ','
OPTIONALLY ENCLOSED by '"'
LINES TERMINATED by '\n'
IGNORE 1 ROWS
(ear_num, id, standard_id);

-- 查询父类型
select substr(sire_id, 1,6), count(substr(sire_id, 1, 6)) from cattle_info group by substr(sire_id, 1,6);
-- 
select substr(sire_id, 1,6), count(substr(sire_id, 1, 6))
from cattle_info
group by substr(sire_id, 1,6)
ORDER BY count(substr(sire_id, 1,6)) desc;
-- 
select substr(dam_id, 1,6), count(substr(dam_id, 1, 6)) from cattle_info group by substr(dam_id, 1,6);
-- 
select substr(dam_id, 1,6), count(substr(dam_id, 1, 6)) from cattle_info 
group by substr(dam_id, 1,6)
 order by count(substr(dam_id, 1,6)) 
 desc limit 10;


-- 测试用表
CREATE TABLE IF NOT EXISTS cattle_info_test (
    standard_id VARCHAR(50) PRIMARY KEY,  -- 标准牛号 (设置为主键，确保唯一性)
    birth_date DATE,                      -- 出生日期
    sire_id VARCHAR(50),                  -- 父号
    dam_id VARCHAR(50),                   -- 母号
    maternal_grandsire_id VARCHAR(50),    -- 外祖父号
    maternal_granddam_id VARCHAR(50),     -- 外祖母号
    maternal_granddam_sire_id VARCHAR(50) NULL -- 外祖母父号
);