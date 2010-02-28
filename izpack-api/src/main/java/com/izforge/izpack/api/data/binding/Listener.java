package com.izforge.izpack.api.data.binding;

/**
 * Listener entity
 *
 * @author Anthonin Bonnefoy
 */
public class Listener
{
    private String classname;

    private Stage stage;

    private OsModel os;

    private String jar;

    public Listener(String classname, Stage stage, OsModel os, String jar)
    {
        this.classname = classname;
        this.stage = stage;
        this.os = os;
        this.jar = jar;
    }

    public String getClassname()
    {
        return classname;
    }

    public String getJar()
    {
        return jar;
    }

    public Stage getStage()
    {
        return stage;
    }

    public OsModel getOs()
    {
        return os;
    }

    @Override
    public String toString()
    {
        return "Listener{" +
                "classname='" + classname + '\'' +
                ", stage=" + stage +
                ", os=" + os +
                '}';
    }
}
