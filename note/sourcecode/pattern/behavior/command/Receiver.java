package com.codebase.pattern.behavior.command;

/**
 * @author: lazecoding
 * @date: 2021/4/5 14:38
 * @description: 抽象接收者
 */
public abstract class Receiver {

    /**
     * 抽象接收者，定义每个接收者都必须完成的业务
     */
    public abstract void doSomething();
}
