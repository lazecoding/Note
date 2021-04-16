package com.codebase.pattern.behavior.visitor;

/**
 * @author: lazecoding
 * @date: 2021/4/16 21:25
 * @description: 具体元素
 */
public class ConcreteElement1 extends Element {
    @Override
    public void doSomething() {
        //业务处理
        System.out.println("ConcreteElement1 doSomething ...");
    }

    /**
     * 允许那个访问者访问
     *
     * @param visitor
     */
    @Override
    public void accept(IVisitor visitor) {
        visitor.visit(this);
    }
}