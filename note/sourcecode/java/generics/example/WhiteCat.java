package com.codebase.javaSE.generics.example;

public class WhiteCat extends Cat{
    private String name = "white cat";

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
