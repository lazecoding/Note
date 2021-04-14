package com.codebase.pattern.structure.facade;

/**
 * @author: lazecoding
 * @date: 2021/4/14 20:39
 * @description: 外观对象
 */
public class Facade {
    /**
     * 被委托的对象
     */
    private ClassA a = new ClassA();

    /**
     * 被委托的对象
     */
    private ClassB b = new ClassB();

    /**
     * 对外提供的方法
     */
    public void doAllThing() {
        this.a.doSomething();
        this.b.doSomething();
    }
}
