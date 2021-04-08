package com.codebase.pattern.behavior.strategy;

/**
 * @author: lazecoding
 * @date: 2021/4/8 21:38
 * @description: 具体策略
 */
public class ConcreteStrategy2 implements Strategy {
    @Override
    public void doSomething() {
        System.out.println("ConcreteStrategy2 doSomething ...");
    }
}