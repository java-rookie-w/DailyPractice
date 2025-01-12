package org.wang.mianshi.collectiontest;

import java.util.ArrayList;
import java.util.List;

public class List2 {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(0, 3);
        list.add(1,4);
        System.out.println(list);
        list.set(0, 5);
        list.set(1, 6);
        System.out.println(list);
    }
}
