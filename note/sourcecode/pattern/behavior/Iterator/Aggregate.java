package com.codebase.pattern.behavior.Iterator;

/**
 * @author: lazecoding
 * @date: 2021/4/12 21:50
 * @description: 抽象容器
 */
public interface Aggregate {

    /**
     * 创建迭代器
     * @return
     */
    Iterator createIterator();
}
