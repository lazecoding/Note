package com.codebase.pattern.structure.bridge;

/**
 * @author: lazecoding
 * @date: 2021/4/18 14:42
 * @description: 抽象角色
 */
public abstract class Abstraction {
    /**
     * 定义对实现角色的引用
     */
    private Implementor imp;

    public Abstraction(Implementor _imp) {
        this.imp = _imp;
    }

    public void request() {
        this.imp.doSomething();
    }

    public Implementor getImp() {
        return imp;
    }
}