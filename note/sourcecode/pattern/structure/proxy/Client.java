package com.codebase.pattern.structure.proxy;

/**
 * @author: lazecoding
 * @date: 2021/3/28 18:19
 * @description:
 */
public class Client {

    public static void main(String[] args) {
        RealSubject subject = new RealSubject();
        Proxy proxy = new Proxy(subject);
        proxy.request();
    }
}
