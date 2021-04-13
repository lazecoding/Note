package com.codebase.pattern.structure.composite;

/**
 * @author: lazecoding
 * @date: 2021/4/13 21:40
 * @description: 抽象组件
 */
public abstract class Component {
    protected String name;

    public Component(String name) {
        this.name = name;
    }

    public void print() {
        print(0);
    }

    abstract void print(int level);

    abstract public void add(Component component);

    abstract public void remove(Component component);
}