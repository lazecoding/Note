package com.codebase.pattern.behavior.observer;

/**
 * @author: lazecoding
 * @date: 2021/4/14 20:06
 * @description: 具体被观察者
 */
public class ConcreteSubject extends Subject {

    /**
     * 业务行为
     */
    public void doSomething() {
        /*
         * do something
         */
        super.notifyObservers();
    }
}