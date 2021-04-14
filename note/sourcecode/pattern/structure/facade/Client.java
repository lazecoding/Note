package com.codebase.pattern.structure.facade;

/**
 * @author: lazecoding
 * @date: 2021/4/14 20:41
 * @description:
 */
public class Client {
    public static void main(String[] args) {
        Facade facade = new Facade();
        facade.doAllThing();
    }
}
