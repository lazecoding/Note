package com.codebase.pattern.behavior.state;

/**
 * @author: lazecoding
 * @date: 2021/4/16 22:05
 * @description: 抽象状态
 */
public abstract class State {
    /**
     * 定义一个环境角色，提供子类访问
     */
    protected Context context;

    /**
     * 设置环境角色
     *
     * @param _context
     */
    public void setContext(Context _context) {
        this.context = _context;
    }

    /**
     * 行为1
     */
    public abstract void handle1();

    /**
     * 行为2
     */
    public abstract void handle2();
}
