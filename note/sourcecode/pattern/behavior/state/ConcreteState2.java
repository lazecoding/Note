package com.codebase.pattern.behavior.state;

/**
 * @author: lazecoding
 * @date: 2021/4/16 22:05
 * @description: 具体状态
 */
public class ConcreteState2 extends State {
    @Override
    public void handle1() {
        // 设置当前状态为 state1
        super.context.setCurrentState(Context.STATE1);
        // 过渡到 state1 状态，由 Context 实现
        super.context.handle1();
    }

    @Override
    public void handle2() {
        // 本状态下必须处理的逻辑
    }
}