package com.izforge.izpack.merge.jar;

import com.izforge.izpack.api.merge.Mergeable;
import com.izforge.izpack.core.container.TestMergeContainer;
import com.izforge.izpack.matcher.MergeMatcher;
import com.izforge.izpack.merge.resolve.MergeableResolver;
import com.izforge.izpack.merge.resolve.PathResolver;
import com.izforge.izpack.merge.resolve.ResolveUtils;
import com.izforge.izpack.test.Container;
import com.izforge.izpack.test.junit.PicoRunner;
import org.hamcrest.core.Is;
import org.hamcrest.text.StringContains;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test for merge jar
 *
 * @author Anthonin Bonnefoy
 */
@RunWith(PicoRunner.class)
@Container(TestMergeContainer.class)
public class JarMergeTest
{
    private PathResolver pathResolver;
    private MergeableResolver mergeableResolver;

    public JarMergeTest(PathResolver pathResolver, MergeableResolver mergeableResolver)
    {
        this.pathResolver = pathResolver;
        this.mergeableResolver = mergeableResolver;
    }

    @Test
    public void testAddJarContent() throws Exception
    {
        URL resource = ClassLoader.getSystemResource("com/izforge/izpack/merge/test/jar-hellopanel-1.0-SNAPSHOT.jar");
        Mergeable jarMerge = mergeableResolver.getMergeableFromURL(resource);
        assertThat(jarMerge, MergeMatcher.isMergeableContainingFiles("jar/izforge/izpack/panels/hello/HelloPanel.class")
        );
    }

    @Test
    public void testMergeClassFromJarFile() throws Exception
    {
        List<Mergeable> jarMergeList = pathResolver.getMergeableFromPath("org/fest/assertions/Assert.class");

        assertThat(jarMergeList.size(), Is.is(1));

        Mergeable jarMerge = jarMergeList.get(0);
        assertThat(jarMerge, MergeMatcher.isMergeableContainingFiles("org/fest/assertions/Assert.class"));
    }
    
    @Test
    public void testMergeClassFromJarFileWithDestination() throws Exception
    {
        List<Mergeable> jarMergeList = pathResolver.getMergeableFromPath("org/fest/assertions/Assert.class", "foo/SomeRandomClass.class");

        assertThat(jarMergeList.size(), Is.is(1));

        Mergeable jarMerge = jarMergeList.get(0);
        assertThat(jarMerge, MergeMatcher.isMergeableContainingFiles("foo/SomeRandomClass.class"));
    }

    @Test
    public void testMergeJarFoundDynamicallyLoaded() throws Exception
    {
        URL urlJar = ClassLoader.getSystemResource("com/izforge/izpack/merge/test/jar-hellopanel-1.0-SNAPSHOT.jar");
        URLClassLoader loader = URLClassLoader.newInstance(new URL[]{urlJar}, ClassLoader.getSystemClassLoader());

        Mergeable jarMerge = mergeableResolver.getMergeableFromURLWithDestination(loader.getResource("jar/izforge/"), "com/dest");

        assertThat(jarMerge, MergeMatcher.isMergeableContainingFiles("com/dest/izpack/panels/hello/HelloPanel.class"));
    }


    @Test
    public void testFindPanelInJar() throws Exception
    {
        URL resource = ClassLoader.getSystemResource("com/izforge/izpack/merge/test/izpack-panel-5.0.0-SNAPSHOT.jar");
        Mergeable jarMerge = mergeableResolver.getMergeableFromURL(resource);
        File file = jarMerge.find(new FileFilter()
        {
            public boolean accept(File pathname)
            {
                return pathname.isDirectory() ||
                        pathname.getName().replaceAll(".class", "").equalsIgnoreCase("CheckedHelloPanel");
            }
        });
        assertThat(ResolveUtils.convertPathToPosixPath(file.getAbsolutePath()), StringContains.containsString("com/izforge/izpack/panels/checkedhello/CheckedHelloPanel.class"));
    }


    @Test
    public void testFindFileInJarFoundWithURL() throws Exception
    {
        URL urlJar = ClassLoader.getSystemResource("com/izforge/izpack/merge/test/jar-hellopanel-1.0-SNAPSHOT.jar");
        URLClassLoader loader = URLClassLoader.newInstance(new URL[]{urlJar}, ClassLoader.getSystemClassLoader());

        Mergeable jarMerge = mergeableResolver.getMergeableFromURL(loader.getResource("jar/izforge"));
        File file = jarMerge.find(new FileFilter()
        {
            public boolean accept(File pathname)
            {
                return pathname.getName().matches(".*HelloPanel\\.class") || pathname.isDirectory();
            }
        });
        assertThat(file.getName(), Is.is("HelloPanel.class"));
    }

    @Test
    public void testRegexpMatch() throws Exception
    {
        String toCheckKo = "com/izforge/izpack/panels/installationgroup/";
        String toCheckOk = "com/izforge/izpack/panels/install/InstallationPanel.class";
        String regexp = "com/izforge/izpack/panels/install/+(.*)";
        assertThat(toCheckKo.matches(regexp), Is.is(false));
        assertThat(toCheckOk.matches(regexp), Is.is(true));

        assertThat("test//Double//".replaceAll("//", "/"), Is.is("test/Double/"));


    }
}
