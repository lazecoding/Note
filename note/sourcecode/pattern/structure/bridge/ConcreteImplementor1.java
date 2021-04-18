package com.codebase.pattern.structure.bridge;

/**
 * @author: lazecoding
 * @date: 2021/4/18 14:41
 * @description:
 */
public class ConcreteImplementor1 implements Implementor {
    @Override
    public void doSomething() {
        //业务逻辑处理
        System.out.println("ConcreteImplementor1 doSomething ...");
    }

    @Override
    public void doAnything() {
        //业务逻辑处理
        System.out.println("ConcreteImplementor1 doAnything ...");
    }
}
