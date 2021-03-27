package com.codebase.pattern.behavior.template;

/**
 * @author: lazecoding
 * @date: 2021/3/27 19:44
 * @description:
 */
public class ConcreteClass1 extends AbstractClass {

    @Override
    protected void doAnything() {
        //业务逻辑处理
        System.out.println("ConcreteClass1 doAnything ...");
    }
    @Override
    protected void doSomething() {
        //业务逻辑处理
        System.out.println("ConcreteClass1 doSomething ...");
    }
}