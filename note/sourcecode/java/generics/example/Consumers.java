package com.codebase.javaSE.generics.example;

import java.util.ArrayList;
import java.util.List;

public class Consumers {

    /*public int count(List<Animal> animals){
        return animals.size();
    }*/

    /*public <T> int count(List<T extends Animal> animals){
        return animals.size();
    }*/

    public int count(List<? extends Animal> animals){
        return animals.size();
    }

    public void check(){
        List<Dog> dogList = new ArrayList<>();
        Dog dog = new Dog();
        dogList.add(dog);
        count(dogList);
    }

    public static void main(String[] args) {
        /**********实体***********/
        Shop<Animal> shop = new ShopImpl<>();
        Cat cat = new Cat();
        Dog dog = new Dog();
        Shop<Cat> shopForCat = new ShopImpl<>();
        Shop<Dog> shopForDog = new ShopImpl<>();
        CatShop<Cat> catShop = new CatShopImpl<>();
        WhiteCat whiteCat = new WhiteCat();
        Storekeeper storekeeper = new Storekeeper();
        /**********实体***********/

        /**********简单泛型***********/
        System.out.println("我想要一只 " + shop.get(cat).getName());
        System.out.println("我想要一只 " + shop.get(dog).getName());
        System.out.println("我想要一只 " + shop.get(whiteCat).getName());
        /**********简单泛型***********/
        System.out.println("--------------------------------");
        /**********边界***********/
        System.out.println("我想要一只" + catShop.get(whiteCat).getName());
        System.out.println("我是一只猫吗? " + catShop.get(whiteCat).isCat());
        /**********边界***********/
        System.out.println("--------------------------------");
        /**********上边界泛型***********/
        storekeeper.open(shop);
        storekeeper.open(shopForCat);
        //storekeeper.open(shopForDog);   -- 类型检查异常
        //System.out.println("我想要一只"+catShop.get(dog).getName());   -- 类型检查异常
        /**********上边界泛型***********/
        System.out.println("--------------------------------");
        // super
        /**********下边界泛型***********/
        //storekeeper.close(shop);          -- 类型检查异常
        //storekeeper.close(shopForCat);    -- 类型检查异常
        storekeeper.close(shopForDog);
        /**********下边界泛型***********/

    }



}


