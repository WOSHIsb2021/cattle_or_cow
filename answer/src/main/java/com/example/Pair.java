package com.example;

import java.util.Objects;

// 用于存储一对个体的ID，作为亲缘系数缓存的键
class Pair<K, V> {
    final K key;   // 第一个个体ID
    final V value; // 第二个体ID

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // 同一对象
        if (o == null || getClass() != o.getClass()) return false; // 类型不匹配
        Pair<?, ?> pair = (Pair<?, ?>) o;
        // 亲缘系数计算不区分顺序 (A,B) 和 (B,A) 相同
        return (Objects.equals(key, pair.key) && Objects.equals(value, pair.value)) ||
               (Objects.equals(key, pair.value) && Objects.equals(value, pair.key));
    }

    @Override
    public int hashCode() {
        // 生成与顺序无关的哈希码
        int h1 = Objects.hashCode(key);
        int h2 = Objects.hashCode(value);
        return h1 ^ h2; // 使用异或操作，简单且满足交换律
    }
}