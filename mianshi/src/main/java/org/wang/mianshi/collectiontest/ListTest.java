package org.wang.mianshi.collectiontest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ListTest {
    public static void main(String[] args) {
//        List<Integer> list = new ArrayList<>();
//        list.add(2,1);

        List<Integer> list2 = new ArrayList<>(Collections.nCopies(10, 3));
        System.out.println(list2);

        List<Integer> list3 = new LinkedList<>();
        // linkedlist默认使用尾插
        list3.add(1);

        CopyOnWriteArrayList<String> cwa = new CopyOnWriteArrayList<>();
        cwa.add("c");
        cwa.get(0);

    }
}
