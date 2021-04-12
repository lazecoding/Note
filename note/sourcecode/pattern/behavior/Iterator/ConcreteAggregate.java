package com.codebase.pattern.behavior.Iterator;

/**
 * @author: lazecoding
 * @date: 2021/4/12 21:51
 * @description: 具体容器
 */
public class ConcreteAggregate implements Aggregate {

    private Integer[] items;

    public ConcreteAggregate() {
        items = new Integer[10];
        for (int i = 0; i < items.length; i++) {
            items[i] = i;
        }
    }

    /**
     * 创建迭代器
     * @return
     */
    @Override
    public Iterator createIterator() {
        return new ConcreteIterator<Integer>(items);
    }
}