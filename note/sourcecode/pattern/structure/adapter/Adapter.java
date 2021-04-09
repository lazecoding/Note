package com.codebase.pattern.structure.adapter;

/**
 * @author: lazecoding
 * @date: 2021/4/9 22:10
 * @description: 适配器
 */
public class Adapter extends Adaptee implements Target {
    @Override
    public void request() {
        super.doSomething();
    }
}
