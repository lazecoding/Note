package com.codebase.pattern.structure.decoration;

/**
 * @author: lazecoding
 * @date: 2021/4/6 20:57
 * @description: 具体构件
 */
public class ConcreteComponent extends Component{
    @Override
    public void operate() {
        System.out.println("ConcreteComponent Something ...");
    }
}
