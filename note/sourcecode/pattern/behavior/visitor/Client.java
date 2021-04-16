package com.codebase.pattern.behavior.visitor;

/**
 * @author: lazecoding
 * @date: 2021/4/16 21:26
 * @description: 场景类
 */
public class Client {
    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            //获得元素对象
            Element el = ObjectStruture.createElement();
            //接受访问者访问
            el.accept(new Visitor());
        }
    }
}
