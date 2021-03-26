package com.codebase.pattern.create.prototype;

/**
 * @author: lazecoding
 * @date: 2021/3/26 23:01
 * @description:
 */
public class Client {

    public static void main(String[] args) {
        // 产生一个对象
        PrototypeClass prototypeClass = new PrototypeClass();
        // 拷贝一个对象
        PrototypeClass clonePrototypeClass = prototypeClass.clone();
    }

}
