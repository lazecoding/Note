package com.codebase.pattern.behavior.memento;

/**
 * @author: lazecoding
 * @date: 2021/4/14 21:17
 * @description: 备忘录
 */
public class Memento {
    /**
     * 发起人的内部状态
     */
    private String state = "";

    /**
     * 构造函数传递参数
     *
     * @param _state
     */
    public Memento(String _state) {
        this.state = _state;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}