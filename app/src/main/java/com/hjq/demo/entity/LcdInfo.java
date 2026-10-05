package com.hjq.demo.entity;

public class LcdInfo {

    private int width;
    private int height;
    private int type;
    private String typeDescription;

    public LcdInfo(int width, int height, int type, String typeDescription) {
        this.width = width;
        this.height = height;
        this.type = type;
        this.typeDescription = typeDescription;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getType() {
        return type;
    }

    public String getTypeDescription() {
        return typeDescription;
    }

    @Override
    public String toString() {
        return "LcdInfo{" +
                "width=" + width +
                ", height=" + height +
                ", type=" + type +
                ", typeDescription='" + typeDescription + '\'' +
                '}';
    }
}
