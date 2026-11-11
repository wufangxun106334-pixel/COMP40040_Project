package model;

public class Item {
    private String type;

    public Item(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }

    // 让日志打印可读（原先默认输出形如 model.Item@2b0871e5）
    @Override
    public String toString() {
        return type;
    }
    
}
