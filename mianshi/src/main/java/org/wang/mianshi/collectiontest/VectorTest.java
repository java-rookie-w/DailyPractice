package org.wang.mianshi.collectiontest;

import java.util.Vector;

public class VectorTest {
    public static void main(String[] args) {
        Vector<String> vector = new Vector<>();
        vector.add("a");
        vector.add("a");
        vector.add("b");
        vector.add("b");
        System.out.println(vector);
        System.out.println(vector.get(1));
        System.out.println(vector.size());

    }
}
