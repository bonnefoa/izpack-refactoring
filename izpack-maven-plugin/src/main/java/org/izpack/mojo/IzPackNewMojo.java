package org.izpack.mojo;

import com.izforge.izpack.api.data.Info;
import com.izforge.izpack.api.data.binding.IzpackProjectInstaller;
import com.izforge.izpack.compiler.CompilerConfig;
import com.izforge.izpack.compiler.container.CompilerContainer;
import com.izforge.izpack.compiler.data.CompilerData;
import org.apache.maven.model.Developer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.util.List;

/**
 * Mojo for izpack
 *
 * @author Anthonin Bonnefoy
 * @goal izpack
 * @phase package
 * @requiresDependencyResolution test
 */
public class IzPackNewMojo extends AbstractMojo
{
    /**
      * The Maven Project Object
      *
      * @parameter expression="${project}" default-value="${project}"
      * @required
      * @readonly
      */
     private MavenProject project;

    /**
     * Format compression. Choices are bzip2, default
     *
     * @parameter default-value="default"
     */
    private String comprFormat;

    /**
     * Kind of installation. Choices are standard (default) or web
     *
     * @parameter default-value="standard"
     */
    private String kind;

    /**
     * Location of the IzPack installation file
     *
     * @parameter default-value="${basedir}/src/main/izpack/install.xml"
     */
    private String installFile;

    /**
     * Base directory of compilation process
     *
     * @parameter default-value="${project.build.directory}/staging"
     */
    private String baseDir;

    /**
     * Output where compilation result will be situate
     *
     * @parameter default-value="${project.build.directory}/${project.build.finalName}-installer.jar"
     */
    private String output;

    /**
     * Compression level of the installation. Desactivated by default (-1)
     *
     * @parameter default-value="-1"
     */
    private int comprLevel;


    public void execute() throws MojoExecutionException, MojoFailureException
    {
        CompilerData compilerData = initCompilerData();
        CompilerContainer compilerContainer = new CompilerContainer();
        compilerContainer.initBindings();
        compilerContainer.addConfig("installFile", installFile);
        compilerContainer.getComponent(IzpackProjectInstaller.class);
        compilerContainer.addComponent(CompilerData.class, compilerData);

        CompilerConfig compilerConfig = compilerContainer.getComponent(CompilerConfig.class);
        try
        {
            compilerConfig.executeCompiler();
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    private CompilerData initCompilerData()
    {
        Info info = new Info();
        if(project != null)
        {
            if(project.getDevelopers() != null)
            {
                for(Developer dev : (List<Developer>)project.getDevelopers())
                {
                  info.addAuthor(new Info.Author(dev.getName(), dev.getEmail()));
                }
            }
            info.setAppURL(project.getUrl());
        }
        return new CompilerData(comprFormat, kind, installFile, null, baseDir, output, comprLevel, info);
    }
}