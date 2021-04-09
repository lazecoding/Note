package com.codebase.pattern.structure.adapter;

/**
 * @author: lazecoding
 * @date: 2021/4/9 22:11
 * @description: 场景类
 */
public class Client {
    public static void main(String[] args) {
        // 原有的业务逻辑
        Target target = new ConcreteTarget();
        target.request();
        System.out.println("****我是分割线****");
        // 现在增加了适配器角色后的业务逻辑
        Target target2 = new Adapter();
        target2.request();
    }
}
