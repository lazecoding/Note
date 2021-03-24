package com.codebase.pattern.create.builder;

/**
 * @author: lazecoding
 * @date: 2021/3/24 21:01
 * @description: 抽象建造者
 */
public abstract class Builder {
    //设置产品的不同部分，以获得不同的产品
    public abstract void setPart();

    //建造产品
    public abstract Product buildProduct();
}
