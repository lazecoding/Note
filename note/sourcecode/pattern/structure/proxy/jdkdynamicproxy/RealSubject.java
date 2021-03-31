package com.codebase.pattern.structure.proxy.jdkdynamicproxy;

/**
 * @author: lazecoding
 * @date: 2021/3/31 21:57
 * @description:
 */
public class RealSubject  implements Subject{
    @Override
    public void doThing() {
        System.out.println("RealSubject doThings ..");
    }
}
