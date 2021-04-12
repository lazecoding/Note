package com.codebase.pattern.behavior.Iterator;

/**
 * @author: lazecoding
 * @date: 2021/4/12 21:52
 * @description: 场景类
 */
public class Client {

    public static void main(String[] args) {
        Aggregate aggregate = new ConcreteAggregate();
        Iterator<Integer> iterator = aggregate.createIterator();
        while (iterator.hasNext()) {
            System.out.println(iterator.next());
        }
    }
}