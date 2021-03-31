package com.codebase.pattern.structure.proxy.jdkdynamicproxy;

import java.lang.reflect.Proxy;

/**
 * @author: lazecoding
 * @date: 2021/3/31 22:02
 * @description:
 */
public class Client {
    public static void main(String[] args) {
        Subject subject = new RealSubject();
        ProxyHander proxyHander = new ProxyHander(subject);

        ClassLoader classLoader = subject.getClass().getClassLoader();
        Subject proxy = (Subject) Proxy.newProxyInstance(classLoader, new Class[]{Subject.class},proxyHander);
        proxy.doThing();
    }
}
