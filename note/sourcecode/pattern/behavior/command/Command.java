package com.codebase.pattern.behavior.command;

/**
 * @author: lazecoding
 * @date: 2021/4/5 14:40
 * @description: 抽象命令
 */
public abstract class Command {

    /**
     * 每个命令类都必须有一个执行命令的方法
     */
    public abstract void execute();
}
