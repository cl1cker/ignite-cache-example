package org.example;

public class Test {
    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getGlobal().setLevel(java.util.logging.Level.OFF);
        System.out.println("\n-----Starting Pojo cache test-----");
        PersonPojoTest.run();
        System.out.println("\n-----Starting Proto cache test-----");
        PersonProtoTest.run();
    }
}
