package com.codebase.pattern.behavior.observer;

/**
 * @author: lazecoding
 * @date: 2021/4/14 20:06
 * @description: 具体观察者
 */
public class ConcreteObserver implements Observer {
    @Override
    public void update() {
        System.out.println("ConcreteObserver 笨蛋 猪头 干活啦 ...");
    }
}