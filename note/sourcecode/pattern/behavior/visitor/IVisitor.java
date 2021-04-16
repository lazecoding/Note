package com.codebase.pattern.behavior.visitor;

/**
 * @author: lazecoding
 * @date: 2021/4/16 21:25
 * @description: 抽象访问者
 */
public interface IVisitor {
    /**
     * 可以访问哪些元素
     *
     * @param el1
     */
    public void visit(ConcreteElement1 el1);

    /**
     * 可以访问哪些元素
     *
     * @param el2
     */
    public void visit(ConcreteElement2 el2);
}