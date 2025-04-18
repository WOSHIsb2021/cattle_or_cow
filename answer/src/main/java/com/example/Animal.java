package com.example;

class Animal {
    String id; // 个体ID
    String sireId; // 父号ID
    String damId; // 母号ID

    public Animal(String id, String sireId, String damId) {
        this.id = id;
        // 将数据库中的 "0" 或 null 或空字符串视为空父/母号标记，并去除前后空格
        this.sireId = (sireId == null || sireId.equals("0") || sireId.trim().isEmpty()) ? null : sireId.trim();
        this.damId = (damId == null || damId.equals("0") || damId.trim().isEmpty()) ? null : damId.trim();
    }

    // Getter 方法
    public String getId() {
        return id;
    }

    public String getSireId() {
        return sireId;
    }

    public String getDamId() {
        return damId;
    }

    @Override
    public String toString() { // 便于调试输出
        return "Animal [ID=" + id + ", 父号=" + sireId + ", 母号=" + damId + "]";
    }
}