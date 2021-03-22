package com.codebase.pattern.create.simplefactory;

public class Client {
    public static void main(String[] args) {
        Animal animal = AnimalFactory.createAnimal("dog");
        animal.eat();
    }
}
