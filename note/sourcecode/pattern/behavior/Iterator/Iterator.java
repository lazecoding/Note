package com.codebase.pattern.behavior.Iterator;

/**
 * @author: lazecoding
 * @date: 2021/4/12 21:48
 * @description: 抽象迭代器
 */
public interface Iterator<Item> {

    Item next();

    boolean hasNext();
}
