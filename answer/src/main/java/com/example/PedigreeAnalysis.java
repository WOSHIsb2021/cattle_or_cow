package com.example;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class PedigreeAnalysis {

    // --- 数据库连接配置 ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/cow?useSSL=false&serverTimezone=UTC"; // 数据库URL
    private static final String DB_USER = "root";      // 数据库用户名
    private static final String DB_PASSWORD = "root";  // 数据库密码
    private static final String TABLE_NAME = "cattle_info"; // 母牛系谱数据表名
    private static final String ID_COL = "standard_id"; // 个体ID列名
    private static final String SIRE_COL = "sire_id";   // 父号ID列名
    private static final String DAM_COL = "dam_id";     // 母号ID列名

    // --- 日志文件配置 ---
    private static final String LOG_FILE = "pedigree_analysis_log.txt"; // 日志文件名
    private static PrintWriter logWriter; // 静态日志写入器实例

    public static void main(String[] args) {
        // 使用 try-with-resources 自动管理日志写入器的生命周期
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, false))) { // false = 覆盖日志文件
            logWriter = writer; // 将实例赋给静态变量

            logInfo("程序启动，开始系谱分析...");

            // 1. 从数据库加载系谱数据
            Map<String, Animal> pedigree = loadPedigreeFromDB();

            // 检查数据是否加载成功
            if (pedigree.isEmpty()) {
                logInfo("未能加载系谱数据或数据为空。程序退出。");
                return; // 退出程序
            }

            logInfo("成功从数据库加载 " + pedigree.size() + " 条个体记录。");

            // 2. 创建近交系数计算器实例，并传入日志写入器
            InbreedingCalculator calculator = new InbreedingCalculator(pedigree, logWriter);

            // 3. 计算并记录每个个体的近交系数
            logInfo("\n开始计算近交系数:");
            int calculatedCount = 0; // 成功计算的数量
            int errorCount = 0;      // 计算出错的数量
            for (String animalId : pedigree.keySet()) {
                // 调用计算器获取近交系数
                double f = calculator.getInbreedingCoefficient(animalId);

                // 检查计算结果是否有效 (非 NaN)
                if (!Double.isNaN(f)) {
                   logInfo(String.format("个体 ID: %s, 近交系数 (F): %.6f", animalId, f));
                   calculatedCount++;
                   // 可选: 将计算结果更新回数据库
                //    updateInbreedingCoefficientInDB(animalId, f);
                } else {
                   // 如果返回 NaN，则记录错误信息
                   logInfo(String.format("个体 ID: %s, 近交系数 (F): 计算错误", animalId));
                   errorCount++;
                }
            }

            // 4. 输出总结信息到日志
            logInfo(String.format("\n计算完成。成功计算 %d 个个体，计算错误 %d 个个体。", calculatedCount, errorCount));
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
     * 从数据库加载系谱数据
     * @return 包含系谱信息的 Map<个体ID, Animal对象>
     */
    private static Map<String, Animal> loadPedigreeFromDB() {
        Map<String, Animal> pedigree = new HashMap<>();
        // 构建 SQL 查询语句
        String query = String.format("SELECT `%s`, `%s`, `%s` FROM `%s`",
                                     ID_COL, SIRE_COL, DAM_COL, TABLE_NAME);

        logInfo("尝试连接数据库: " + DB_URL);
        Connection conn = null; // 在 try 外部声明以便 finally 中可以访问
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD); // 建立连接
            stmt = conn.createStatement(); // 创建 Statement
            logInfo("数据库连接成功。执行查询: " + query);
            rs = stmt.executeQuery(query); // 执行查询

            int count = 0;
            // 遍历查询结果
            while (rs.next()) {
                String id = rs.getString(ID_COL);
                String sireId = rs.getString(SIRE_COL);
                String damId = rs.getString(DAM_COL);

                // 检查个体ID是否有效
                if (id != null && !id.trim().isEmpty()) {
                    // 创建 Animal 对象并存入 Map (trim() 去除前后空格)
                    pedigree.put(id.trim(), new Animal(id.trim(), sireId, damId));
                    count++;
                } else {
                    logWarn("发现记录的个体ID为空或仅包含空格，已跳过。");
                }
            }
            logInfo("成功从数据库表 " + TABLE_NAME + " 加载了 " + count + " 条有效记录。");

        } catch (SQLException e) {
            // 记录数据库操作错误
            logError("数据库错误：加载系谱数据失败。", e);
            // 返回空 Map，主程序会处理
        } finally {
            // 在 finally 块中确保资源被关闭，无论是否发生异常
            try { if (rs != null) rs.close(); } catch (SQLException e) { logError("关闭 ResultSet 时出错", e); }
            try { if (stmt != null) stmt.close(); } catch (SQLException e) { logError("关闭 Statement 时出错", e); }
            try { if (conn != null) conn.close(); } catch (SQLException e) { logError("关闭 Connection 时出错", e); }
            logInfo("数据库资源已关闭。");
        }
        return pedigree;
    }

     // 可选：将计算出的近交系数更新回数据库的方法
    /* 
    private static void updateInbreedingCoefficientInDB(String animalId, double fValue) {
        logInfo(String.format("尝试将个体 %s 的近交系数 %.6f 更新到数据库", animalId, fValue));
        // 假设你的表有一个名为 'inbreeding_coefficient' 的列
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

    // --- 日志辅助方法 ---
    /** 记录普通信息 */
    private static void logInfo(String message) {
        writeToLog("INFO: " + message);
    }

    /** 记录警告信息 */
    private static void logWarn(String message) {
         writeToLog("WARN: " + message);
    }

    /** 记录错误信息，包含异常堆栈 */
    private static void logError(String message, Throwable t) {
        writeToLog("ERROR: " + message);
        // 如果 logWriter 有效且存在异常对象，则打印堆栈跟踪
        if (t != null && logWriter != null) {
            t.printStackTrace(logWriter); // 将堆栈跟踪打印到日志文件
            logWriter.flush(); // 确保立即写入
        } else if (t != null) {
            t.printStackTrace(); // 如果日志写入器无效，则打印到控制台作为后备
        }
    }

    /** 核心写入日志文件的方法，添加时间戳 */
    private static void writeToLog(String message) {
        if (logWriter != null) {
            // 添加时间戳和消息级别
            logWriter.println(java.time.LocalDateTime.now() + " - " + message);
            logWriter.flush(); // 确保消息被立即写入文件
        } else {
            // 如果日志写入器未初始化（例如在 main 方法开始前调用或文件打开失败），则输出到控制台
            System.out.println("(日志记录器不可用) " + message);
        }
    }
}