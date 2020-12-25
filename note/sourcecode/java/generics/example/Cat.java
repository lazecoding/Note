package com.codebase.javaSE.generics.example;

public class Cat extends Animal {
    private String name = "cat";

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public boolean isCat(){
        return true;
    }
}
