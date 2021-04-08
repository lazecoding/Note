package com.codebase.pattern.behavior.strategy;

/**
 * @author: lazecoding
 * @date: 2021/4/8 21:39
 * @description: 封装橘色
 */
public class Context {
    /**
     * 抽象策略
     */
    private Strategy strategy = null;

    /**
     * 构造函数注入具体策略
     * @param _strategy
     */
    public Context(Strategy _strategy) {
        this.strategy = _strategy;
    }

    /**
     * 封装后的策略方法
     */
    public void doAnythinig() {
        this.strategy.doSomething();
    }
}