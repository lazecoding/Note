package com.codebase.pattern.behavior.state;

/**
 * @author: lazecoding
 * @date: 2021/4/16 22:06
 * @description: 场景类
 */
public class Client {
    public static void main(String[] args) {
        // 定义环境角色
        Context context = new Context();
        // 初始化状态
        context.setCurrentState(new ConcreteState1());
        // 行为执行
        context.handle1();
        context.handle2();
    }
}