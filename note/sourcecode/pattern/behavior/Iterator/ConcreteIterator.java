package com.codebase.pattern.behavior.Iterator;

/**
 * @author: lazecoding
 * @date: 2021/4/12 21:48
 * @description: 具体迭代器
 */
public class ConcreteIterator<Item> implements Iterator {

    private Item[] items;
    private int position = 0;

    public ConcreteIterator(Item[] items) {
        this.items = items;
    }

    @Override
    public Object next() {
        return items[position++];
    }

    @Override
    public boolean hasNext() {
        return position < items.length;
    }
}
