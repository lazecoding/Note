package com.codebase.pattern.structure.decoration;

/**
 * @author: lazecoding
 * @date: 2021/4/6 21:00
 * @description: 具体装饰者
 */
public class ConcreteDecorator2 extends Decorator {
    /**
     * 定义被修饰者
     * @param _component
     */
    public ConcreteDecorator2(Component _component) {
        super(_component);
    }

    /**
     * 定义自己的修饰方法
     */
    private void method2() {
        System.out.println("method2 修饰");
    }

    @Override
    public void operate() {
        super.operate();
        this.method2();
    }
}
