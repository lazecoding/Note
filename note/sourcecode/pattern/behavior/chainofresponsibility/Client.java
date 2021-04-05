package com.codebase.pattern.behavior.chainofresponsibility;

/**
 * @author: lazecoding
 * @date: 2021/4/5 17:32
 * @description:
 */
public class Client {
    public static void main(String[] args) {

        // 声明所有的处理节点
        Handler handler1 = new ConcreteHandler1();
        Handler handler2 = new ConcreteHandler2();
        Handler handler3 = new ConcreteHandler3();

        // 设置链中的阶段顺序 1-->2-->3
        handler1.setNext(handler2);
        handler2.setNext(handler3);

        // 创建请求

        Request request = new Request(Level.LEVEL2);

        // 提交请求
        handler1.handleMessage(request);
    }
}