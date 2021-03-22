package com.codebase.pattern.create.factory;

/**
 * @author: lazecoding
 * @date: 2021/3/21 21:13
 * @description:
 */
public class ConcreteFactory extends Factory {
    @Override
    public <T extends Product> T createProduct(Class<T> c) {
        Product product = null;
        try {
            product = (Product) Class.forName(c.getName()).newInstance();
        } catch (Exception e) {
            // 异常处理
        }
        return (T) product;
    }
}