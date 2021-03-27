package com.codebase.pattern.behavior.template;

/**
 * @author: lazecoding
 * @date: 2021/3/27 19:43
 * @description:
 */
public abstract class AbstractClass {
    /**
     * 基本方法
     */
    protected abstract void doSomething();

    /**
     * 基本方法
     */
    protected abstract void doAnything();

    /**
     * 模板方法
     */
    public void templateMethod() {
        /*
         * 调用基本方法，完成相关的逻辑
         */
        this.doAnything();
        this.doSomething();
    }
}
