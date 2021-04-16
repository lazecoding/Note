package com.codebase.pattern.behavior.visitor;

/**
 * @author: lazecoding
 * @date: 2021/4/16 21:24
 * @description: 抽象元素
 */
public abstract class Element {
    //定义业务逻辑
    public abstract void doSomething();
    //允许谁来访问
    public abstract void accept(IVisitor visitor);
}
