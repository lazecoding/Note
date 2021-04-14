package com.codebase.pattern.behavior.observer;

/**
 * @author: lazecoding
 * @date: 2021/4/14 20:07
 * @description:
 */
public class Client {
    public static void main(String[] args) {
        // 创建一个被观察者
        ConcreteSubject subject = new ConcreteSubject();
        // 定义一个观察者
        Observer observer= new ConcreteObserver();
        // 观察者观察被观察者
        subject.registerObserver(observer);
        // 观察者开始活动了
        subject.doSomething();
    }
}
