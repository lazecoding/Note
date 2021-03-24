package com.codebase.pattern.create.builder;

/**
 * @author: lazecoding
 * @date: 2021/3/24 21:01
 * @description: 具体建造者
 */
public class ConcreteBuilder extends Builder {
    private Product product = new Product();

    //设置产品零件
    @Override
    public void setPart() {
        /*
         * 产品类内的逻辑处理
         */
    }

    //组建一个产品
    @Override
    public Product buildProduct() {
        return product;
    }
}
