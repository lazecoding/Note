package com.codebase.pattern.behavior.visitor;

/**
 * @author: lazecoding
 * @date: 2021/4/16 21:26
 * @description: 具体访问者
 */
public class Visitor implements IVisitor {

    /**
     * 访问 el1 元素
     * @param el1
     */
    @Override
    public void visit(ConcreteElement1 el1) {
        el1.doSomething();
    }

    /**
     * 访问 el2 元素
     * @param el2
     */
    @Override
    public void visit(ConcreteElement2 el2) {
        el2.doSomething();
    }
}
