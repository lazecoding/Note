package com.codebase.pattern.behavior.command;

/**
 * @author: lazecoding
 * @date: 2021/4/5 14:39
 * @description: 具体接收者
 */
public class ConcreteReciver2 extends Receiver{

    @Override
    public void doSomething(){
        System.out.println("ConcreteReciver1 doSomething ...");
    }
}
