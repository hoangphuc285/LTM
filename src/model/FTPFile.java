package model;

public class FTPFile {
    private String name;
    private long size;
    private boolean isDirectory;

    public FTPFile(String name, long size, boolean isDirectory) {
        this.name = name;
        this.size = size;
        this.isDirectory = isDirectory;
    }

    public String getName() { return name; }
    public long getSize() { return size; }
    public boolean isDirectory() { return isDirectory; }

    @Override
    public String toString() {
        return (isDirectory ? "[DIR]  " : "[FILE] ") + name + " (" + size + " bytes)";
    }
}