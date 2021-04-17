package com.codebase.pattern.behavior.Interpreter;

/**
 * @author: lazecoding
 * @date: 2021/4/17 22:03
 * @description: 抽象解释器
 */
public abstract class Expression {
    /**
     * 解释接口
     * @param str
     * @return
     */
    public abstract boolean interpret(String str);
}
