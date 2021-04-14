package com.codebase.pattern.behavior.observer;

import java.util.Vector;

/**
 * @author: lazecoding
 * @date: 2021/4/14 20:05
 * @description: 抽象被观察者
 */
public abstract class Subject {
    /**
     * 定义一个观察者数组
     */
    private Vector<Observer> obsVector = new Vector();

    /**
     * 增加一个观察者
     *
     * @param observer
     */
    public void registerObserver(Observer observer) {
        this.obsVector.add(observer);
    }

    //

    /**
     * 删除一个观察者
     *
     * @param observer
     */
    public void removeObserver(Observer observer) {
        this.obsVector.remove(observer);
    }

    /**
     * 通知所有观察者
     */
    public void notifyObservers() {
        for (Observer observer : this.obsVector) {
            observer.update();
        }
    }
}
