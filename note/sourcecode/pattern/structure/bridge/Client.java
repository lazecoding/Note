package com.codebase.pattern.structure.bridge;

/**
 * @author: lazecoding
 * @date: 2021/4/18 14:43
 * @description:
 */
public class Client {
    public static void main(String[] args) {
        // 定义一个实现化角色
        Implementor imp = new ConcreteImplementor1();
        // 定义一个抽象化角色
        Abstraction abs = new RefinedAbstraction(imp);
        // 执行
        abs.request();
    }
}
