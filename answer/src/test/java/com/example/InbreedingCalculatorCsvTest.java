package com.example;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // 每个类一个实例，@BeforeAll 可以非静态
class InbreedingCalculatorCsvTest {

    // CSV 文件名常量
    private static final String PEDIGREE_CSV_FILENAME = "pedigree_data.csv";
    private static final String ID_MAPPING_CSV_FILENAME = "id_mapping.csv";

    // 成员变量存储加载的数据和计算器
    private Map<String, String> idMapping; // 存储 ID 映射 (InternalID -> StandardID)
    private Map<String, Animal> pedigree; // 存储处理后的系谱数据
    private InbreedingCalculator calculator;

    @BeforeAll
    void loadDataAndSetup() throws IOException {
        // 1. 加载 ID 映射
        idMapping = loadIdMappingFromCsv(ID_MAPPING_CSV_FILENAME);
        assertNotNull(idMapping, "ID mapping map should not be null after loading.");
        System.out.println("Loaded " + idMapping.size() + " ID mapping records.");

        // 2. 加载系谱数据，并应用 ID 映射
        pedigree = loadPedigreeFromCsv(PEDIGREE_CSV_FILENAME, idMapping);
        assertNotNull(pedigree, "Pedigree map should not be null after loading.");
        assertFalse(pedigree.isEmpty(), "Pedigree map should not be empty after loading.");
        System.out.println("Loaded and processed " + pedigree.size() + " pedigree records.");

        // 3. 创建计算器实例
        // 使用 PrintWriter(System.out, true) 将计算器的日志输出到控制台，方便测试时查看
        PrintWriter testLogger = new PrintWriter(System.out, true);
        calculator = new InbreedingCalculator(pedigree, testLogger);
        // --- 添加调试打印 ---
        System.out.println("\n--- Debugging Pedigree Data for X, Y, Z, P1, P2, P3 ---");
        String[] debugIds = { "X", "Y", "Z", "P1", "P2", "P3" };
        for (String id : debugIds) {
            Animal animal = pedigree.get(id);
            if (animal != null) {
                System.out.println("Animal Found: " + animal); // 使用 Animal 的 toString() 方法
            } else {
                System.out.println("Animal NOT Found: " + id);
            }
        }
        System.out.println("--- End Debugging Pedigree Data ---\n");
        // --- 调试结束 ---

        assertNotNull(calculator, "InbreedingCalculator instance should be created."); // 原有断言保持


    }

    /**
     * 从 CSV 文件加载 ID 映射 (母牛编号 -> 标准牛号).
     * 假设 CSV 格式: 耳号,母牛编号,标准牛号 (忽略耳号).
     * 会去除 '#' 及其之后的内容.
     *
     * @param filename 资源目录下的 CSV 文件名.
     * @return Map<String, String> 其中 key 是 母牛编号 (Internal ID), value 是 标准牛号
     *         (Standard ID).
     * @throws IOException 如果文件读取失败.
     */
    private Map<String, String> loadIdMappingFromCsv(String filename) throws IOException {
        Map<String, String> mapping = new HashMap<>();
        InputStream is = getClass().getClassLoader().getResourceAsStream(filename);

        if (is == null) {
            throw new FileNotFoundException("Test resource file not found: " + filename);
        }

        try (InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {

            String line;
            boolean headerSkipped = false;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue; // 跳过标题行
                }
                line = line.trim();
                if (line.isEmpty())
                    continue; // 跳过空行

                // 去除行尾注释
                int commentIndex = line.indexOf('#');
                if (commentIndex != -1) {
                    line = line.substring(0, commentIndex).trim(); // 只取注释前的内容并再次 trim
                }
                if (line.isEmpty())
                    continue; // 如果去除注释后行为空，也跳过

                String[] parts = line.split(",", -1); // 按逗号分割，保留末尾空字符串
                // 需要 母牛编号 (索引1) 和 标准牛号 (索引2)
                if (parts.length >= 3) {
                    String internalId = parts[1].trim(); // 母牛编号
                    String standardId = parts[2].trim(); // 标准牛号

                    if (!internalId.isEmpty() && !standardId.isEmpty()) {
                        // 如果内部ID已存在，打印警告并覆盖
                        if (mapping.containsKey(internalId)) {
                            System.err.println("WARN (Line " + lineNumber + "): Duplicate internal ID '" + internalId
                                    + "' found in mapping file. Overwriting previous mapping '"
                                    + mapping.get(internalId) + "' with '" + standardId + "'.");
                        }
                        mapping.put(internalId, standardId);
                    } else {
                        System.err.println(
                                "Skipping mapping row " + lineNumber + " with empty internal or standard ID: " + line);
                    }
                } else {
                    System.err.println("Skipping malformed mapping row " + lineNumber + ": " + line);
                }
            }
        }
        return mapping;
    }

    /**
     * 从 CSV 文件加载系谱数据, 并应用 ID 映射.
     * 假设 CSV 格式: 标准牛号,出生日期,父号,母号,... (只关心 0, 2, 3 列)
     * 会去除各字段中 '#' 及其之后的内容.
     *
     * @param filename  资源目录下的系谱 CSV 文件名.
     * @param idMapping 用于转换父号和母号的映射 Map.
     * @return Map<String, Animal> 系谱数据.
     * @throws IOException 如果文件读取失败.
     */
    private Map<String, Animal> loadPedigreeFromCsv(String filename, Map<String, String> idMapping) throws IOException {
        Map<String, Animal> loadedPedigree = new HashMap<>();
        InputStream is = getClass().getClassLoader().getResourceAsStream(filename);

        if (is == null) {
            throw new FileNotFoundException("Test resource file not found: " + filename);
        }

        try (InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {

            String line;
            boolean headerSkipped = false;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue; // 跳过标题行
                }

                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue; // 跳过空行或整行注释

                // 注意：这里不再对整行去除注释，因为注释可能出现在字段内部（虽然不规范）
                // 而是在获取每个字段后处理

                String[] parts = line.split(",", -1); // 按逗号分割
                // 需要 标准牛号 (索引0), 父号 (索引2), 母号 (索引3)
                if (parts.length >= 4) {
                    // 读取并初步处理每个需要的字段
                    String id = parts[0].trim();
                    String originalSireId = parts[2].trim();
                    String originalDamId = parts[3].trim();

                    // 清理每个字段中的注释 (如果存在)
                    id = cleanComment(id);
                    originalSireId = cleanComment(originalSireId);
                    originalDamId = cleanComment(originalDamId);

                    if (id.isEmpty()) {
                        System.err.println("Skipping pedigree row " + lineNumber
                                + " with empty standard ID after cleaning: " + line);
                        continue; // 个体ID不能为空
                    }

                    // --- 应用 ID 映射 ---
                    // 如果原始 Sire ID 在映射表中，则使用映射后的 Standard ID，否则使用原始 ID
                    String resolvedSireId = idMapping.getOrDefault(originalSireId, originalSireId);
                    // 如果原始 Dam ID 在映射表中，则使用映射后的 Standard ID，否则使用原始 ID
                    String resolvedDamId = idMapping.getOrDefault(originalDamId, originalDamId);

                    // --- 创建 Animal 对象 ---
                    // Animal 构造函数会处理 "0", null, 或空字符串，将它们转换为 null
                    Animal animal = new Animal(id, resolvedSireId, resolvedDamId);
                    if (loadedPedigree.containsKey(id)) {
                        System.err.println("WARN (Line " + lineNumber + "): Duplicate standard ID '" + id
                                + "' found in pedigree file. Overwriting previous entry.");
                    }
                    loadedPedigree.put(animal.getId(), animal);

                } else {
                    System.err.println("Skipping malformed pedigree row " + lineNumber + ": " + line);
                }
            }
        }
        return loadedPedigree;
    }

    /**
     * 辅助方法：去除字符串中第一个 '#' 及之后的所有内容，并 trim.
     * 
     * @param input 输入字符串
     * @return 清理后的字符串
     */
    private String cleanComment(String input) {
        if (input == null) {
            return null;
        }
        int commentIndex = input.indexOf('#');
        if (commentIndex != -1) {
            return input.substring(0, commentIndex).trim();
        }
        return input.trim(); // 如果没有注释，直接 trim
    }

    // --- 测试用例 ---
    // 保留之前的测试用例，它们现在将在应用了 ID 映射的数据上运行

    @Test
    @DisplayName("测试个体 X (半同胞父母 Y, Z)")
    void testInbreeding_X_HalfSibMating() {
        double expectedInbreeding = 0.125; // 根据手动计算 f_YZ = 0.125
        double actualInbreeding = calculator.getInbreedingCoefficient("X");
        assertEquals(expectedInbreeding, actualInbreeding, 0.00001,
                "Inbreeding for X (half-sib parents) should be 0.125");
    }

    @Test
    @DisplayName("测试个体 Y (父母 P1, P2 - 假设无关)")
    void testInbreeding_Y_UnrelatedParents() {
        double expectedInbreeding = 0.0; // F_Y = f_P1P2 = 0 (假设 P1, P2 是无关基础个体)
        double actualInbreeding = calculator.getInbreedingCoefficient("Y");
        assertEquals(expectedInbreeding, actualInbreeding, 0.00001, "Inbreeding for Y should be 0");
    }

    @Test
    @DisplayName("测试个体 HOCHNF37XC010X000001 (检查映射后的母号)")
    void testInbreeding_MappedDam() {
        // F_001 = f(HO840M3234522255, mapped_211558) = f(HO840M3234522255,
        // HOCHNF37XC010T000XXX)
        // HO840M3234522255 父母未知 -> F=0, f_Self=0.5
        // HOCHNF37XC010T000XXX 父母是 P1, P2 -> F=f_P1P2=0, f_Self=0.5
        // f(HO840M, HOCHNF37XC010T) = 0.5 * (f(HO840M, P1) + f(HO840M, P2)) = 0.5 * (0
        // + 0) = 0
        double expectedInbreeding = 0.0; // 假设映射后的父母仍然无关
        double actualInbreeding = calculator.getInbreedingCoefficient("HOCHNF37XC010X000001");
        assertEquals(expectedInbreeding, actualInbreeding, 0.00001,
                "Inbreeding for HOCHNF37XC010X000001 should be 0 after mapping");

        // 验证 Animal 对象中的父/母 ID 是否正确
        Animal animal = pedigree.get("HOCHNF37XC010X000001");
        assertNotNull(animal);
        // 父号应该没有被映射改变 (因为它不在 id_mapping.csv 的 "母牛编号" 列)
        assertEquals("HO840M3234522255", animal.getSireId());
        // 母号 211558 应该被映射为 HOCHNF37XC010T000XXX
        assertEquals("HOCHNF37XC010T000XXX", animal.getDamId());
    }

    @Test
    @DisplayName("测试个体 W (母号需要映射)")
    void testInbreeding_W_MappedDam() {
        // F_W = f(P4, mapped_dam_standard)
        // mapped_dam_standard 的父母是 P1, P2
        // f(P4, mapped_dam_standard) = 0.5 * (f(P4, P1) + f(P4, P2)) = 0.5 * (0 + 0) =
        // 0
        double expectedInbreeding = 0.0;
        double actualInbreeding = calculator.getInbreedingCoefficient("W");
        assertEquals(expectedInbreeding, actualInbreeding, 0.00001, "Inbreeding for W should be 0 after dam mapping");

        Animal animalW = pedigree.get("W");
        assertNotNull(animalW);
        assertEquals("P4", animalW.getSireId());
        assertEquals("mapped_dam_standard", animalW.getDamId()); // 验证母号已被映射
    }

    @Test
    @DisplayName("测试基础个体 P1")
    void testInbreeding_P1_Founder() {
        double expectedInbreeding = 0.0;
        double actualInbreeding = calculator.getInbreedingCoefficient("P1");
        assertEquals(expectedInbreeding, actualInbreeding, 0.00001, "Inbreeding for founder P1 should be 0");
    }

    @Test
    @DisplayName("测试个体 HOCHNF37XC010X000034 (父母信息不全)")
    void testInbreeding_MissingParents() {
        double expectedInbreeding = 0.0; // 缺少母号，无法计算亲缘系数
        double actualInbreeding = calculator.getInbreedingCoefficient("HOCHNF37XC010X000034");
        assertEquals(expectedInbreeding, actualInbreeding, 0.00001,
                "Inbreeding for HOCHNF37XC010X000034 should be 0 due to missing dam");
    }

    @Test
    @DisplayName("测试不存在的个体 ID")
    void testInbreeding_NonExistent() {
        double expectedInbreeding = 0.0; // 计算器对不存在的个体返回 0
        double actualInbreeding = calculator.getInbreedingCoefficient("NON_EXISTENT_ID");
        assertEquals(expectedInbreeding, actualInbreeding, 0.00001, "Inbreeding for a non-existent ID should be 0");
    }

}