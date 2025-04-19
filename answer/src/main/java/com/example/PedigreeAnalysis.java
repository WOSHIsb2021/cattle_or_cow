package com.example;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime; // Import for timestamp 时间戳

public class PedigreeAnalysis {

    // --- 数据库连接配置 ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/cow?useSSL=false&serverTimezone=UTC"; // 数据库URL
    private static final String DB_USER = "root"; // 数据库用户名
    private static final String DB_PASSWORD = "root"; // 数据库密码

    // --- 表和列名配置 ---
    private static final String CATTLE_TABLE_NAME = "cattle_info"; // 母牛系谱数据表名
    private static final String ID_COL = "standard_id"; // 个体标准ID列名
    private static final String SIRE_COL = "sire_id"; // 父号ID列名 (可能需要映射)
    private static final String DAM_COL = "dam_id"; // 母号ID列名 (可能需要映射)

    private static final String MAPPING_TABLE_NAME = "num_comp_tb"; // 牛号对应表名
    private static final String MAPPING_INTERNAL_ID_COL = "id"; // 对应表中的母牛编号 (需要被映射的ID)
    private static final String MAPPING_STANDARD_ID_COL = "standard_id"; // 对应表中的标准牛号 (目标ID)
    // private static final String MAPPING_EAR_NUM_COL = "ear_num"; // 耳号
    // (暂时不需要映射，后面3可以添加)

    // --- 日志文件配置 ---
    private static final String LOG_FILE = "pedigree_analysis_log.txt"; // 日志文件名
    private static PrintWriter logWriter; // 静态日志写入器实例

    public static void main(String[] args) {
        // 使用 try-with-resources 自动管理日志写入器的生命周期
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, false))) { // false = 覆盖日志文件
            logWriter = writer; // 将实例赋给静态变量

            logInfo("程序启动，开始系谱分析...");

            // 1. 从数据库加载 ID 映射表
            Map<String, String> idMapping = loadIdMappingFromDB();
            logInfo("加载了 " + idMapping.size() + " 条 ID 映射记录。");

            // 2. 从数据库加载系谱数据，并应用 ID 映射
            Map<String, Animal> pedigree = loadPedigreeFromDB(idMapping);

            // 检查数据是否加载成功
            if (pedigree.isEmpty()) {
                logInfo("未能加载系谱数据或数据为空。程序退出。");
                return; // 退出程序
            }

            logInfo("成功从数据库加载并处理了 " + pedigree.size() + " 条个体记录。");

            // 3. 创建近交系数计算器实例，并传入日志写入器
            InbreedingCalculator calculator = new InbreedingCalculator(pedigree, logWriter);

            // 4. 计算并记录每个个体的近交系数
            logInfo("\n开始计算近交系数:");
            int calculatedCount = 0; // 成功计算的数量
            int errorCount = 0; // 计算出错的数量
            for (String animalId : pedigree.keySet()) {
                // 调用计算器获取近交系数
                double f = calculator.getInbreedingCoefficient(animalId);

                // 检查计算结果是否有效 (非 NaN)
                if (!Double.isNaN(f)) {
                    logInfo(String.format("个体 ID: %s, 近交系数 (F): %.6f", animalId, f));
                    calculatedCount++;
                    // 可选: 将计算结果更新回数据库
                    // updateInbreedingCoefficientInDB(animalId, f);
                } else {
                    // 如果返回 NaN，则记录错误信息
                    logWarn(String.format("个体 ID: %s, 近交系数 (F): 计算错误或无法计算 (可能由于循环或深度)", animalId));
                    errorCount++;
                }
            }

            // 5. 输出总结信息到日志
            logInfo(String.format("\n计算完成。成功计算 %d 个个体，计算错误/无法计算 %d 个个体。", calculatedCount, errorCount));
            logInfo("详细日志已写入文件: " + LOG_FILE);

        } catch (IOException e) {
            // 如果无法创建或写入日志文件，则在控制台输出错误
            System.err.println("致命错误：无法打开或写入日志文件 " + LOG_FILE);
            e.printStackTrace();
        } finally {
            // try-with-resources 会自动关闭 writer, 这里仅显式置空表明不再使用
            logWriter = null;
            System.out.println("程序执行完毕，请查看日志文件: " + LOG_FILE); // 在控制台提示用户日志文件位置
        }
    }

    /**
     * 从 num_comp_tb 加载牛号编号到标准牛号的映射
     * 
     * @return Map<内部ID/母牛编号, 标准ID>
     */
    private static Map<String, String> loadIdMappingFromDB() {
        Map<String, String> idMapping = new HashMap<>();
        // 构建 SQL 查询语句 - 选择需要映射的ID 和 对应的标准ID
        String query = String.format("SELECT `%s`, `%s` FROM `%s` WHERE `%s` IS NOT NULL AND `%s` IS NOT NULL",
                MAPPING_INTERNAL_ID_COL, MAPPING_STANDARD_ID_COL,
                MAPPING_TABLE_NAME,
                MAPPING_INTERNAL_ID_COL, MAPPING_STANDARD_ID_COL); // 确保两列都不为空

        logInfo("尝试从表 " + MAPPING_TABLE_NAME + " 加载 ID 映射...");
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            stmt = conn.createStatement();
            logInfo("数据库连接成功。执行映射查询: " + query);
            rs = stmt.executeQuery(query);

            int count = 0;
            int duplicateCount = 0;
            while (rs.next()) {
                String internalId = rs.getString(MAPPING_INTERNAL_ID_COL);
                String standardId = rs.getString(MAPPING_STANDARD_ID_COL);

                if (internalId != null && !internalId.trim().isEmpty() &&
                        standardId != null && !standardId.trim().isEmpty()) {
                    String trimmedInternalId = internalId.trim();
                    String trimmedStandardId = standardId.trim();

                    // 检查内部ID是否已存在映射，记录警告
                    if (idMapping.containsKey(trimmedInternalId)) {
                        logWarn(String.format("发现重复的内部ID '%s' 在映射表中。原有映射 '%s' -> '%s', 新映射 '%s' -> '%s'. 将使用新的映射。",
                                trimmedInternalId, trimmedInternalId, idMapping.get(trimmedInternalId),
                                trimmedInternalId, trimmedStandardId));
                        duplicateCount++;
                    }
                    idMapping.put(trimmedInternalId, trimmedStandardId);
                    count++;
                }
            }
            logInfo("成功加载 " + count + " 条有效 ID 映射记录。");
            if (duplicateCount > 0) {
                logWarn("共发现 " + duplicateCount + " 个重复的内部 ID，已使用最后读取到的映射。");
            }

        } catch (SQLException e) {
            logError("数据库错误：加载 ID 映射失败。", e);
            // 返回空的 Map，主程序会继续，但不会进行映射
        } finally {
            // 关闭资源
            try {
                if (rs != null)
                    rs.close();
            } catch (SQLException e) {
                logError("关闭 ResultSet 时出错 (ID Map)", e);
            }
            try {
                if (stmt != null)
                    stmt.close();
            } catch (SQLException e) {
                logError("关闭 Statement 时出错 (ID Map)", e);
            }
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
                logError("关闭 Connection 时出错 (ID Map)", e);
            }
            logInfo("ID 映射数据库资源已关闭。");
        }

        return idMapping;
    }

    /**
     * 从数据库加载系谱数据, 并应用 ID 映射
     * 
     * @param idMapping 从 loadIdMappingFromDB() 获取的映射 Map
     * @return 包含系谱信息的 Map<个体标准ID, Animal对象>
     */
    private static Map<String, Animal> loadPedigreeFromDB(Map<String, String> idMapping) {
        Map<String, Animal> pedigree = new HashMap<>();
        // 构建 SQL 查询语句
        String query = String.format("SELECT `%s`, `%s`, `%s` FROM `%s`",
                ID_COL, SIRE_COL, DAM_COL, CATTLE_TABLE_NAME);

        logInfo("尝试连接数据库加载系谱数据: " + DB_URL);
        Connection conn = null; // 在 try 外部声明以便 finally 中可以访问
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD); // 建立连接
            stmt = conn.createStatement(); // 创建 Statement
            logInfo("数据库连接成功。执行系谱查询: " + query);
            rs = stmt.executeQuery(query); // 执行查询

            int recordCount = 0;
            int mappedSireCount = 0;
            int mappedDamCount = 0;

            // 遍历查询结果
            while (rs.next()) {
                recordCount++;
                String id = rs.getString(ID_COL);
                String originalSireId = rs.getString(SIRE_COL);
                String originalDamId = rs.getString(DAM_COL);

                // 检查个体标准ID是否有效
                if (id == null || id.trim().isEmpty()) {
                    logWarn(String.format("发现记录 %d 的个体标准ID为空或仅包含空格，已跳过。", recordCount));
                    continue; // 跳过此记录
                }
                String trimmedId = id.trim();

                // --- 应用 ID 映射 ---
                String resolvedSireId = originalSireId; // 默认使用原始值
                if (originalSireId != null && !originalSireId.trim().isEmpty()) {
                    String trimmedOriginalSireId = originalSireId.trim();
                    String mappedSire = idMapping.get(trimmedOriginalSireId);
                    if (mappedSire != null) {
                        resolvedSireId = mappedSire; // 使用映射后的标准ID
                        if (!trimmedOriginalSireId.equals(resolvedSireId)) { // 仅当实际发生映射时计数
                            mappedSireCount++;
                            logInfo(String.format("个体 %s 的父号 '%s' 映射为 '%s'", trimmedId, trimmedOriginalSireId,
                                    resolvedSireId));
                        }
                    } else {
                        // 如果原始 sire ID 不在映射表中，假定它已经是标准ID
                        resolvedSireId = trimmedOriginalSireId;
                    }
                }

                String resolvedDamId = originalDamId; // 默认使用原始值
                if (originalDamId != null && !originalDamId.trim().isEmpty()) {
                    String trimmedOriginalDamId = originalDamId.trim();
                    // **重点: 查找 dam_id 是否在映射表的 internal_id 列中**
                    String mappedDam = idMapping.get(trimmedOriginalDamId);
                    if (mappedDam != null) {
                        resolvedDamId = mappedDam; // 使用映射后的标准ID
                        if (!trimmedOriginalDamId.equals(resolvedDamId)) { // 仅当实际发生映射时计数
                            mappedDamCount++;
                            logInfo(String.format("个体 %s 的母号 '%s' 映射为 '%s'", trimmedId, trimmedOriginalDamId,
                                    resolvedDamId));
                        }
                    } else {
                        // 如果原始 dam ID 不在映射表中，假定它已经是标准ID (或无法映射)
                        resolvedDamId = trimmedOriginalDamId;
                        // 可选：如果需要，可以记录哪些母号未被映射
                        // logInfo(String.format("个体 %s 的母号 '%s' 未在映射表中找到，使用原始值。", trimmedId,
                        // trimmedOriginalDamId));
                    }
                }
                // --- 映射结束 ---

                // 创建 Animal 对象并存入 Map (使用处理过的 ID)
                // Animal 构造函数内部会处理 "0", null, 空字符串等情况
                pedigree.put(trimmedId, new Animal(trimmedId, resolvedSireId, resolvedDamId));

            }
            logInfo(String.format("处理了 %d 条来自 %s 的记录。加载了 %d 个有效个体。",
                    recordCount, CATTLE_TABLE_NAME, pedigree.size()));
            logInfo(String.format("共映射了 %d 个父号和 %d 个母号。", mappedSireCount, mappedDamCount));

        } catch (SQLException e) {
            // 记录数据库操作错误
            logError("数据库错误：加载系谱数据失败。", e);
            // 返回空 Map，主程序会处理
        } finally {
            // 在 finally 块中确保资源被关闭，无论是否发生异常
            try {
                if (rs != null)
                    rs.close();
            } catch (SQLException e) {
                logError("关闭 ResultSet 时出错 (Pedigree)", e);
            }
            try {
                if (stmt != null)
                    stmt.close();
            } catch (SQLException e) {
                logError("关闭 Statement 时出错 (Pedigree)", e);
            }
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
                logError("关闭 Connection 时出错 (Pedigree)", e);
            }
            logInfo("系谱数据数据库资源已关闭。");
        }
        return pedigree;
    }

    // --- 日志辅助方法 ---
    /** 记录普通信息 */
    private static void logInfo(String message) {
        writeToLog("INFO", message);
    }

    /** 记录警告信息 */
    private static void logWarn(String message) {
        writeToLog("WARN", message);
    }

    /** 记录错误信息，包含异常堆栈 */
    private static void logError(String message, Throwable t) {
        writeToLog("ERROR", message);
        // 如果 logWriter 有效且存在异常对象，则打印堆栈跟踪
        if (t != null && logWriter != null) {
            t.printStackTrace(logWriter); // 将堆栈跟踪打印到日志文件
            logWriter.flush(); // 确保立即写入
        } else if (t != null) {
            t.printStackTrace(); // 如果日志写入器无效，则打印到控制台作为后备
        }
    }

    /** 核心写入日志文件的方法，添加时间戳和级别 */
    private static void writeToLog(String level, String message) {
        if (logWriter != null) {
            // 添加时间戳和消息级别
            logWriter.println(LocalDateTime.now() + " - " + level + ": " + message);
            logWriter.flush(); // 确保消息被立即写入文件
        } else {
            // 如果日志写入器未初始化，则输出到控制台
            System.out.println(LocalDateTime.now() + " - (" + level + " - 日志记录器不可用) " + message);
        }
    }
     // 可选：将计算出的近交系数更新回数据库的方法
     // 表中添加一个名为 'inbreeding_coefficient' 的列再执行此操作
    /* 
    private static void updateInbreedingCoefficientInDB(String animalId, double fValue) {
        logInfo(String.format("尝试将个体 %s 的近交系数 %.6f 更新到数据库", animalId, fValue));
        // 再次强调，要添加列再执行
        String sql = String.format("UPDATE `%s` SET inbreeding_coefficient = ? WHERE `%s` = ?", TABLE_NAME, ID_COL);
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (Double.isNaN(fValue)) { // 处理计算错误的情况
                 pstmt.setNull(1, Types.DOUBLE);
                 logWarn("个体 " + animalId + " 的近交系数计算错误 (NaN)，在数据库中设置为 NULL。");
            } else {
                 pstmt.setDouble(1, fValue);
            }
            pstmt.setString(2, animalId); // 设置 WHERE 条件
            int affectedRows = pstmt.executeUpdate(); // 执行更新

            if (affectedRows > 0) {
                 logInfo("成功更新个体 " + animalId + " 的近交系数。");
            } else {
                 logWarn("更新个体 " + animalId + " 的近交系数时，未找到匹配记录或值未改变。");
            }

        } catch (SQLException e) {
            logError("数据库错误：更新个体 " + animalId + " 的近交系数失败。", e);
        }
    }
    */
}