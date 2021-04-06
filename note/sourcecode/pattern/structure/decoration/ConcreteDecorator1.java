package com.codebase.pattern.structure.decoration;

/**
 * @author: lazecoding
 * @date: 2021/4/6 21:00
 * @description: 具体装饰者
 */
public class ConcreteDecorator1 extends Decorator {

    /**
     * 定义被修饰者
     *
     * @param _component
     */
    public ConcreteDecorator1(Component _component) {
        super(_component);
    }

    /**
     * 定义自己的修饰方法
     */
    private void method1() {
        System.out.println("method1 修饰");
    }

    @Override
    public void operate() {
        this.method1();
        super.operate();
    }
}
