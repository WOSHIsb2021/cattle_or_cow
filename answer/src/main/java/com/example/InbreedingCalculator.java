package com.example;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class InbreedingCalculator {
    private final Map<String, Animal> pedigree; // 存储系谱数据的Map
    private final Map<String, Double> inbreedingCache; // 缓存已计算的近交系数 F_X
    private final Map<Pair<String, String>, Double> coancestryCache; // 缓存已计算的亲缘系数 f_AB
    private final PrintWriter logger; // 日志写入器
    private static final int MAX_DEPTH = 50; // 最大递归深度，防止栈溢出

    /**
     * 构造函数
     * @param pedigree 系谱数据
     * @param logger 日志写入器实例
     */
    public InbreedingCalculator(Map<String, Animal> pedigree, PrintWriter logger) {
        this.pedigree = pedigree;
        this.inbreedingCache = new HashMap<>();
        this.coancestryCache = new HashMap<>();
        this.logger = logger; // 保存日志写入器
    }

    /**
     * 公开方法：获取指定个体的近交系数
     * @param animalId 需要计算近交系数的个体ID
     * @return 近交系数值，如果发生错误则返回 Double.NaN
     */
    public double getInbreedingCoefficient(String animalId) {
        try {
            // 调用递归计算方法
            return calculateInbreedingRecursive(animalId, 0);
        } catch (StackOverflowError e) {
            // 捕获栈溢出错误，通常因为系谱循环或深度过大
            logError(String.format("错误: 计算个体 %s 的近交系数时发生栈溢出，可能存在系谱循环或递归过深。",
                    animalId), e);
            return Double.NaN; // 返回 NaN 表示计算错误
        }
    }

    /**
     * 递归计算近交系数 F_X
     * F_X = f_SD (个体 X 的近交系数等于其父母 S 和 D 之间的亲缘系数)
     * @param animalId 个体ID
     * @param depth 当前递归深度
     * @return 近交系数值
     */
    private double calculateInbreedingRecursive(String animalId, int depth) {
        // 检查递归深度是否超限
        if (depth > MAX_DEPTH) {
            logWarn(String.format("警告: 计算个体 %s 的近交系数时超过最大递归深度(%d)。假定其值为 0。", animalId, MAX_DEPTH));
            return 0.0; // 返回 0 或抛出异常
        }

        // 1. 检查近交系数缓存
        if (inbreedingCache.containsKey(animalId)) {
            return inbreedingCache.get(animalId);
        }

        // 2. 获取个体信息
        Animal animal = pedigree.get(animalId);

        // 3. 基础情况: 个体不存在或父母信息不全
        if (animal == null || animal.getSireId() == null || animal.getDamId() == null) {
            inbreedingCache.put(animalId, 0.0); // 假定未知父母的个体近交系数为 0
            return 0.0;
        }

        String sireId = animal.getSireId();
        String damId = animal.getDamId();

        // 4. 核心计算: F_X = f_SD (调用亲缘系数计算)
        double coancestrySD = calculateCoancestryRecursive(sireId, damId, depth + 1);

        // 5. 存入缓存并返回
        inbreedingCache.put(animalId, coancestrySD);
        return coancestrySD;
    }

    /**
     * 递归计算两个个体之间的亲缘系数 f_AB
     * f_AB = 0.5 * (f_{A, Sire_B} + f_{A, Dam_B}) (假设追溯 B 的父母)
     * f_AA = 0.5 * (1 + F_A) (个体与自身的亲缘系数)
     * @param id1 第一个个体ID
     * @param id2 第二个体ID
     * @param depth 当前递归深度
     * @return 亲缘系数值
     */
    private double calculateCoancestryRecursive(String id1, String id2, int depth) {
        // 检查递归深度
        if (depth > MAX_DEPTH) {
            logWarn(String.format("警告: 计算个体 %s 和 %s 的亲缘系数时超过最大递归深度(%d)。假定其值为 0。", id1, id2, MAX_DEPTH));
            return 0.0;
        }

        // 1. 确保缓存键的顺序一致性 (id1 <= id2)
        String keyId1 = (id1.compareTo(id2) <= 0) ? id1 : id2;
        String keyId2 = (id1.compareTo(id2) <= 0) ? id2 : id1;
        Pair<String, String> cacheKey = new Pair<>(keyId1, keyId2);

        // 2. 检查亲缘系数缓存
        if (coancestryCache.containsKey(cacheKey)) {
            return coancestryCache.get(cacheKey);
        }

        // 3. 基础情况: 任意一个体是未知的 (ID 为 null 或不在系谱中)
        if (id1 == null || id2 == null || !pedigree.containsKey(id1) || !pedigree.containsKey(id2)) {
            coancestryCache.put(cacheKey, 0.0);
            return 0.0;
        }

        double coancestry;
        // 4. 递归计算
        // 情况 1: 计算个体与自身的亲缘系数 f_AA = 0.5 * (1 + F_A)
        if (id1.equals(id2)) {
            // 需要先递归计算该个体的近交系数 F_A
            double inbreedingA = calculateInbreedingRecursive(id1, depth + 1);
            coancestry = 0.5 * (1.0 + inbreedingA);
        }
        // 情况 2: 计算不同个体之间的亲缘系数 f_AB = 0.5 * (f_{A, Sire_B} + f_{A, Dam_B})
        else {
            // 选择 id2 来追溯其父母 (也可以选择 id1)
            Animal animal2 = pedigree.get(id2);
            String sireId2 = animal2.getSireId();
            String damId2 = animal2.getDamId();

            // 如果 id2 的父母都未知，则亲缘系数为 0
            if (sireId2 == null && damId2 == null) {
                coancestry = 0.0;
            } else {
                // 递归计算 f_{id1, Sire_id2} 和 f_{id1, Dam_id2}
                double coanS = (sireId2 == null) ? 0.0 : calculateCoancestryRecursive(id1, sireId2, depth + 1);
                double coanD = (damId2 == null) ? 0.0 : calculateCoancestryRecursive(id1, damId2, depth + 1);
                coancestry = 0.5 * (coanS + coanD);
            }
        }

        // 5. 存入缓存并返回
        coancestryCache.put(cacheKey, coancestry);
        return coancestry;
    }

    // --- 日志辅助方法 (内部使用) ---
    /** 记录警告信息 */
    private void logWarn(String message) {
        if (logger != null) {
            logger.println(java.time.LocalDateTime.now() + " - WARN (Calculator): " + message);
            logger.flush();
        } else {
            System.err.println("WARN (Calculator - Logger unavailable): " + message); // 后备方案
        }
    }

    /** 记录错误信息及堆栈跟踪 */
     private void logError(String message, Throwable t) {
        if (logger != null) {
            logger.println(java.time.LocalDateTime.now() + " - ERROR (Calculator): " + message);
            if (t != null) {
                t.printStackTrace(logger); // 将堆栈跟踪打印到日志文件
            }
            logger.flush();
        } else {
            System.err.println("ERROR (Calculator - Logger unavailable): " + message); // 后备方案
            if (t != null) {
                t.printStackTrace(); // 打印到控制台
            }
        }
    }
}