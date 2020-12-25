package com.codebase.javaSE.generics.example;

public class Dog extends Animal{
    private String name = "dog";

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
