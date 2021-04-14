package com.codebase.pattern.behavior.memento;

/**
 * @author: lazecoding
 * @date: 2021/4/14 21:17
 * @description: 发起人
 */
public class Originator {
    //内部状态
    private String state = "";

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    /**
     * 创建一个备忘录
     * @return
     */
    public Memento createMemento() {
        return new Memento(this.state);
    }

    /**
     * 恢复一个备忘录
     * @param _memento
     */
    public void restoreMemento(Memento _memento) {
        this.setState(_memento.getState());
    }
}