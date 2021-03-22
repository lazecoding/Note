package com.codebase.pattern.create.factory;

/**
 * @author: lazecoding
 * @date: 2021/3/21 21:17
 * @description:
 */
public class Client {
    public static void main(String[] args) {
        Factory creator = new ConcreteFactory();
        Product product = creator.createProduct(ConcreteProduct1.class);
        System.out.println(product);
        /*
         * 继续业务处理
         */
    }
}
