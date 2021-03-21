package com.codebase.pattern.create.factory;

/**
 * @author: lazecoding
 * @date: 2021/3/21 21:12
 * @description:
 */
public abstract class Creator {
    /*
     * 创建一个产品对象，其输入参数类型可以自行设置
     * 通常为 String、Enum、Class等，当然也可以为空
     */
    public abstract <T extends Product> T createProduct(Class<T> c);
}
