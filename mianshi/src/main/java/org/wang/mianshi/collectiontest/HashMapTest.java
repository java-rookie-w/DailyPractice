package org.wang.mianshi.collectiontest;

import java.util.HashMap;
import java.util.Map;

public class HashMapTest {
    public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();
        map.put("1", "a");
        map.put("2", "b");
        map.put("3", "c");
        map.get("1");
        map.remove("1");
        System.out.println("1".hashCode());
    }
}
