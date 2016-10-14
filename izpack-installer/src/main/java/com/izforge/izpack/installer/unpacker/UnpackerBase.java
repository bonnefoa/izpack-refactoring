/*
 * IzPack - Copyright 2001-2008 Julien Ponge, All Rights Reserved.
 *
 * http://izpack.org/
 * http://izpack.codehaus.org/
 *
 * Copyright 2007 Dennis Reil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.izforge.izpack.installer.unpacker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import com.izforge.izpack.api.data.AutomatedInstallData;
import com.izforge.izpack.api.data.Blockable;
import com.izforge.izpack.api.data.OverrideType;
import com.izforge.izpack.api.data.Pack;
import com.izforge.izpack.api.data.PackFile;
import com.izforge.izpack.api.data.ResourceManager;
import com.izforge.izpack.api.event.InstallerListener;
import com.izforge.izpack.api.handler.AbstractUIHandler;
import com.izforge.izpack.api.handler.AbstractUIProgressHandler;
import com.izforge.izpack.api.rules.RulesEngine;
import com.izforge.izpack.api.substitutor.VariableSubstitutor;
import com.izforge.izpack.api.unpacker.IDiscardInterruptable;
import com.izforge.izpack.data.ExecutableFile;
import com.izforge.izpack.data.UpdateCheck;
import com.izforge.izpack.installer.data.UninstallData;
import com.izforge.izpack.util.Debug;
import com.izforge.izpack.util.IoHelper;
import com.izforge.izpack.util.OsVersion;
import com.izforge.izpack.util.file.DirectoryScanner;
import com.izforge.izpack.util.file.GlobPatternMapper;
import com.izforge.izpack.util.file.types.FileSet;
import com.izforge.izpack.util.os.FileQueue;
import com.izforge.izpack.util.os.FileQueueMove;

/**
 * Abstract base class for all unpacker implementations.
 *
 * @author Dennis Reil, <izpack@reil-online.de>
 */
public abstract class UnpackerBase implements IUnpacker, IDiscardInterruptable
{
    /**
     * The installdata.
     */
    protected AutomatedInstallData idata;

    /**
     * The installer listener.
     */
    protected AbstractUIProgressHandler handler;

    /**
     * The uninstallation data.
     */
    protected UninstallData udata;

    /**
     * The absolute path of the installation. (NOT the canonical!)
     */
    protected File absolute_installpath;

    /**
     * The absolute path of the source installation jar.
     */
    private File absolutInstallSource;

    /**
     * The result of the operation.
     */
    protected boolean result = true;

    /**
     * The instances of the unpacker objects.
     */
    protected static HashMap<Object, String> instances = new HashMap<Object, String>();

    /**
     * Interrupt flag if global interrupt is desired.
     */
    protected static boolean interruptDesired = false;

    /**
     * Do not perform a interrupt call.
     */
    protected static boolean discardInterrupt = false;

    /**
     * The name of the XML file that specifies the panel langpack
     */
    protected static final String LANG_FILE_NAME = "packsLang.xml";

    public static final String ALIVE = "alive";

    public static final String INTERRUPT = "doInterrupt";

    public static final String INTERRUPTED = "interruppted";

    protected RulesEngine rules;

    protected ResourceManager resourceManager;
    protected VariableSubstitutor variableSubstitutor;

    /**
     * The constructor.
     *
     * @param idata               The installation data.
     * @param rules
     * @param variableSubstitutor
     * @param udata
     */
    public UnpackerBase(AutomatedInstallData idata, ResourceManager resourceManager, RulesEngine rules, VariableSubstitutor variableSubstitutor, UninstallData udata)
    {
        this.idata = idata;
        this.resourceManager = resourceManager;
        this.rules = rules;
        // Initialize the variable substitutor
        this.variableSubstitutor = variableSubstitutor;
        this.udata = udata;
    }

    public void setRules(RulesEngine rules)
    {
        this.rules = rules;
    }

    /**
     * Returns a copy of the active unpacker instances.
     *
     * @return a copy of active unpacker instances
     */
    public static HashMap getRunningInstances()
    {
        synchronized (instances)
        { // Return a shallow copy to prevent a
            // ConcurrentModificationException.
            return (HashMap) (instances.clone());
        }
    }

    /**
     * Adds this to the map of all existent instances of Unpacker.
     */
    protected void addToInstances()
    {
        synchronized (instances)
        {
            instances.put(this, ALIVE);
        }
    }

    /**
     * Removes this from the map of all existent instances of Unpacker.
     */
    protected void removeFromInstances()
    {
        synchronized (instances)
        {
            instances.remove(this);
        }
    }

    /**
     * Initiate interrupt of all alive Unpacker. This method does not interrupt the Unpacker objects
     * else it sets only the interrupt flag for the Unpacker objects. The dispatching of interrupt
     * will be performed by the Unpacker objects self.
     */
    private static void setInterruptAll()
    {
        synchronized (instances)
        {
            for (Object key : instances.keySet())
            {
                if (instances.get(key).equals(ALIVE))
                {
                    instances.put(key, INTERRUPT);
                }
            }
            // Set global flag to allow detection of it in other classes.
            // Do not set it to thread because an exec will then be stoped.
            setInterruptDesired(true);
        }
    }

    /**
     * Initiate interrupt of all alive Unpacker and waits until all Unpacker are interrupted or the
     * wait time has arrived. If the doNotInterrupt flag in InstallerListener is set to true, the
     * interrupt will be discarded.
     *
     * @param waitTime wait time in millisecounds
     * @return true if the interrupt will be performed, false if the interrupt will be discarded
     */
    public static boolean interruptAll(long waitTime)
    {
        long t0 = System.currentTimeMillis();
        if (isDiscardInterrupt())
        {
            return (false);
        }
        setInterruptAll();
        while (!isInterruptReady())
        {
            if (System.currentTimeMillis() - t0 > waitTime)
            {
                return (true);
            }
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
            }
        }
        return (true);
    }

    private static boolean isInterruptReady()
    {
        synchronized (instances)
        {
            for (Object key : instances.keySet())
            {
                if (!instances.get(key).equals(INTERRUPTED))
                {
                    return (false);
                }
            }
            return (true);
        }

    }

    /**
     * Sets the interrupt flag for this Unpacker to INTERRUPTED if the previos state was INTERRUPT
     * or INTERRUPTED and returns whether interrupt was initiate or not.
     *
     * @return whether interrupt was initiate or not
     */
    protected boolean performInterrupted()
    {
        synchronized (instances)
        {
            Object doIt = instances.get(this);
            if (doIt != null && (doIt.equals(INTERRUPT) || doIt.equals(INTERRUPTED)))
            {
                instances.put(this, INTERRUPTED);
                this.result = false;
                return (true);
            }
            return (false);
        }
    }

    /**
     * Returns whether interrupt was initiate or not for this Unpacker.
     *
     * @return whether interrupt was initiate or not
     */
    private boolean shouldInterrupt()
    {
        synchronized (instances)
        {
            Object doIt = instances.get(this);
            if (doIt != null && (doIt.equals(INTERRUPT) || doIt.equals(INTERRUPTED)))
            {
                return (true);
            }
            return (false);
        }

    }

    /**
     * Return the state of the operation.
     *
     * @return true if the operation was successful, false otherwise.
     */
    public boolean getResult()
    {
        return this.result;
    }


    // CUSTOM ACTION STUFF -------------- start -----------------

    /**
     * Informs all listeners which would be informed at the given action type.
     *
     * @param customActions             array of lists with the custom action objects
     * @param action                    identifier for which callback should be called
     * @param file                      first parameter for the call
     * @param packFile                  second parameter for the call
     * @param abstractUIProgressHandler third parameter for the call
     */
    protected void informListeners(List<InstallerListener> customActions, int action, File file,
                                   PackFile packFile, AbstractUIProgressHandler abstractUIProgressHandler) throws Exception
    {
        // Iterate the action list.
        for (InstallerListener installerListener : customActions)
        {
            if (shouldInterrupt())
            {
                return;
            }
            switch (action)
            {
                case InstallerListener.BEFORE_FILE:
                    installerListener.beforeFile(file, packFile);
                    break;
                case InstallerListener.AFTER_FILE:
                    installerListener.afterFile(file, packFile);
                    break;
                case InstallerListener.BEFORE_DIR:
                    installerListener.beforeDir(file, packFile);
                    break;
                case InstallerListener.AFTER_DIR:
                    installerListener.afterDir(file, packFile);
                    break;
            }
        }
    }

    protected void informListeners(List<InstallerListener> customActions, int action, Pack pack,
                                   Integer integer, AbstractUIProgressHandler abstractUIProgressHandler) throws Exception
    {
        for (InstallerListener customAction : customActions)
        {
            switch (action)
            {
                case InstallerListener.BEFORE_PACK:
                    customAction.beforePack(pack, integer,
                            abstractUIProgressHandler);
                    break;
                case InstallerListener.AFTER_PACK:
                    customAction.afterPack(pack, integer,
                            abstractUIProgressHandler);
                    break;
            }
        }
    }

    protected void informListeners(List<InstallerListener> customActions, int action, AutomatedInstallData pack,
                                   Integer integer, AbstractUIProgressHandler abstractUIProgressHandler) throws Exception
    {
        for (InstallerListener customAction : customActions)
        {
            switch (action)
            {
                case InstallerListener.BEFORE_PACKS:
                    customAction.beforePacks(pack, integer, abstractUIProgressHandler);
                    break;
                case InstallerListener.AFTER_PACKS:
                    customAction.afterPacks(pack, abstractUIProgressHandler);
                    break;
            }
        }
    }

    /**
     * Creates the given directory recursive and calls the method "afterDir" of each listener with
     * the current file object and the pack file object. On error an exception is raised.
     *
     * @param dest          the directory which should be created
     * @param pf            current pack file object
     * @param customActions all defined custom actions
     * @return false on error, true else
     * @throws Exception
     */
    protected boolean mkDirsWithEnhancement(File dest, PackFile pf, List<InstallerListener> customActions)
            throws Exception
    {
        String path = "unknown";
        if (dest != null)
        {
            path = dest.getAbsolutePath();
        }
        if (dest != null && !dest.exists() && dest.getParentFile() != null)
        {
            if (dest.getParentFile().exists())
            {
                informListeners(customActions, InstallerListener.BEFORE_DIR, dest, pf, null);
            }
            if (!dest.mkdir())
            {
                mkDirsWithEnhancement(dest.getParentFile(), pf, customActions);
                if (!dest.mkdir())
                {
                    dest = null;
                }
            }
            informListeners(customActions, InstallerListener.AFTER_DIR, dest, pf, null);
        }
        if (dest == null)
        {
            handler.emitError("Error creating directories", "Could not create directory\n" + path);
            handler.stopAction();
            return (false);
        }
        return (true);
    }

    // CUSTOM ACTION STUFF -------------- end -----------------

    /**
     * Returns whether an interrupt request should be discarded or not.
     *
     * @return Returns the discard interrupt flag
     */
    public static synchronized boolean isDiscardInterrupt()
    {
        return discardInterrupt;
    }

    /**
     * Sets the discard interrupt flag.
     *
     * @param di the discard interrupt flag to set
     */
    public synchronized void setDiscardInterrupt(boolean di)
    {
        discardInterrupt = di;
        setInterruptDesired(false);
    }

    /**
     * Returns the interrupt desired state.
     *
     * @return the interrupt desired state
     */
    public static boolean isInterruptDesired()
    {
        return interruptDesired;
    }

    /**
     * @param interruptDesired The interrupt desired flag to set
     */
    private static void setInterruptDesired(boolean interruptDesired)
    {
        UnpackerBase.interruptDesired = interruptDesired;
    }

    public abstract void run();

    protected void performUpdateChecks(ArrayList<UpdateCheck> updatechecks)
    {
        if (updatechecks != null && updatechecks.size() > 0)
        {
            FileSet fileset = new FileSet();
            ArrayList<File> files_to_delete = new ArrayList<File>();
            ArrayList<File> dirs_to_delete = new ArrayList<File>();

            try
            {
                fileset.setDir(new File(idata.getInstallPath()).getAbsoluteFile());

                for (UpdateCheck uc : updatechecks)
                {
                    if (uc.includesList != null)
                    {
                        for (String incl : uc.includesList)
                        {
                            fileset.createInclude().setName(variableSubstitutor.substitute(incl));
                        }
                    }

                    if (uc.excludesList != null)
                    {
                        for (String excl : uc.excludesList)
                        {
                            fileset.createExclude().setName(variableSubstitutor.substitute(excl));
                        }
                    }
                }
                DirectoryScanner ds = fileset.getDirectoryScanner();
                ds.scan();
                String[] srcFiles = ds.getIncludedFiles();
                String[] srcDirs = ds.getIncludedDirectories();

                TreeSet<File> installed_files = new TreeSet<File>();

                for (String fname : this.udata.getInstalledFilesList())
                {
                    File f = new File(fname);

                    if (!f.isAbsolute())
                    {
                        f = new File(this.absolute_installpath, fname);
                    }

                    installed_files.add(f);
                }
                for (String srcFile : srcFiles)
                {
                    File newFile = new File(ds.getBasedir(), srcFile);

                    // skip files we just installed
                    if (installed_files.contains(newFile))
                    {
                        continue;
                    }
                    else
                    {
                        files_to_delete.add(newFile);
                    }
                }
                for (String srcDir : srcDirs)
                {
                    File newDir = new File(ds.getBasedir(), srcDir);

                    // skip directories we just installed
                    if (installed_files.contains(newDir))
                    {
                        continue;
                    }
                    else
                    {
                        dirs_to_delete.add(newDir);
                    }
                }
            }
            catch (Exception e)
            {
                this.handler.emitError("Error while performing update checks", e.getMessage());
            }

            for (File f : files_to_delete)
            {
                f.delete();
            }
            for (File d : dirs_to_delete)
            {
                // Only empty directories will be deleted
                d.delete();
            }
        }
    }

    /**
     * Writes information about the installed packs and the variables at
     * installation time.
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void writeInstallationInformation() throws IOException, ClassNotFoundException
    {
        if (!idata.getInfo().isWriteInstallationInformation())
        {
            Debug.trace("skip writing installation information");
            return;
        }
        Debug.trace("writing installation information");
        String installdir = idata.getInstallPath();

        List<Pack> installedpacks = new ArrayList<Pack>(idata.getSelectedPacks());

        File installationinfo = new File(installdir + File.separator + AutomatedInstallData.INSTALLATION_INFORMATION);
        if (!installationinfo.exists())
        {
            Debug.trace("creating info file" + installationinfo.getAbsolutePath());
            installationinfo.createNewFile();
        }
        else
        {
            Debug.trace("installation information found");
            // read in old information and update
            FileInputStream fin = new FileInputStream(installationinfo);
            ObjectInputStream oin = new ObjectInputStream(fin);

            List<Pack> packs = (List<Pack>) oin.readObject();
            for (Pack pack1 : packs)
            {
                Pack pack = pack1;
                installedpacks.add(pack);
            }
            oin.close();
            fin.close();

        }

        FileOutputStream fout = new FileOutputStream(installationinfo);
        ObjectOutputStream oout = new ObjectOutputStream(fout);
        oout.writeObject(installedpacks);
        /*
        int selectedpackscount = installData.selectedPacks.size();
        for (int i = 0; i < selectedpackscount; i++)
        {
            Pack pack = (Pack) installData.selectedPacks.get(i);
            oout.writeObject(pack);
        }
        */
        oout.writeObject(idata.getVariables());
        Debug.trace("done.");
        oout.close();
        fout.close();
    }

    protected File getAbsolutInstallSource() throws Exception
    {
        if (absolutInstallSource == null)
        {
            URI uri = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            if (!"file".equals(uri.getScheme()))
            {
                throw new Exception("Unexpected scheme in JAR file URI: " + uri);
            }
            absolutInstallSource = new File(uri.getSchemeSpecificPart()).getAbsoluteFile();
            if (absolutInstallSource.getName().endsWith(".jar"))
            {
                absolutInstallSource = absolutInstallSource.getParentFile();
            }
        }
        return absolutInstallSource;
    }

    protected boolean blockableForCurrentOs(PackFile pf)
    {
        return
                (pf.blockable() != Blockable.BLOCKABLE_NONE)
                        && (OsVersion.IS_WINDOWS);
    }

    @Override
    public void setHandler(AbstractUIProgressHandler handler)
    {
        this.handler = handler;
    }

    protected void handleMkDirs(PackFile pf, File dest) throws Exception
    {
        if (!dest.exists())
        {
            // If there are custom actions which would be called
            // at
            // creating a directory, create it recursively.
//            List fileListeners = customActions[customActions.length - 1];
//            if (fileListeners != null && fileListeners.size() > 0)
//            {
//                mkDirsWithEnhancement(dest, pf, customActions);
//            }
//            else
            // Create it in on step.
            {
                if (!dest.mkdirs())
                {
                    handler.emitError("Error creating directories",
                            "Could not create directory\n" + dest.getPath());
                    handler.stopAction();
                    this.result = false;
                    return;
                }
            }
        }
    }

    protected long writeBuffer(PackFile pf, byte[] buffer,
            FileOutputStream out, InputStream pis, long bytesCopied)
    throws IOException
    {
        int maxBytes = (int) Math.min(pf.length() - bytesCopied, buffer.length);
        int bytesInBuffer = pis.read(buffer, 0, maxBytes);
        if (bytesInBuffer == -1)
        {
            throw new IOException("Unexpected end of stream (installer corrupted?)");
        }
        out.write(buffer, 0, bytesInBuffer);
        bytesCopied += bytesInBuffer;

        return bytesCopied;
    }

    protected boolean isOverwriteFile(PackFile pf, File file)
    {
        boolean overwritefile = false;

        // don't overwrite file if the user said so
        if (pf.override() != OverrideType.OVERRIDE_FALSE)
        {
            if (pf.override() == OverrideType.OVERRIDE_TRUE)
            {
                overwritefile = true;
            }
            else if (pf.override() == OverrideType.OVERRIDE_UPDATE)
            {
                // check mtime of involved files
                // (this is not 100% perfect, because the
                // already existing file might
                // still be modified but the new installed
                // is just a bit newer; we would
                // need the creation time of the existing
                // file or record with which mtime
                // it was installed...)
                overwritefile = (file.lastModified() < pf.lastModified());
            }
            else
            {
                int def_choice = -1;

                if (pf.override() == OverrideType.OVERRIDE_ASK_FALSE)
                {
                    def_choice = AbstractUIHandler.ANSWER_NO;
                }
                if (pf.override() == OverrideType.OVERRIDE_ASK_TRUE)
                {
                    def_choice = AbstractUIHandler.ANSWER_YES;
                }

                int answer = handler.askQuestion(idata.getLangpack()
                        .getString("InstallPanel.overwrite.title")
                        + " - " + file.getName(), idata.getLangpack()
                        .getString("InstallPanel.overwrite.question")
                        + file.getAbsolutePath(),
                        AbstractUIHandler.CHOICES_YES_NO, def_choice);

                overwritefile = (answer == AbstractUIHandler.ANSWER_YES);
            }

        }

        return overwritefile;
    }

    protected void handleOverrideRename(PackFile pf, File file)
    {
        if ((file.exists()) && pf.overrideRenameTo() != null)
        {
            GlobPatternMapper mapper = new GlobPatternMapper();
            mapper.setFrom("*");
            mapper.setTo(pf.overrideRenameTo());
            mapper.setCaseSensitive(true);
            String[] newFileNameArr = mapper.mapFileName(file.getName());
            if (newFileNameArr != null)
            {
                String newFileName = newFileNameArr[0];
                File newPathFile = new File(file.getParent(), newFileName);
                if (newPathFile.exists())
                {
                    newPathFile.delete();
                }
                if (!file.renameTo(newPathFile))
                {
                    handler.emitError("Error renaming file", "The file " + file
                            + " could not be renamed to " + newPathFile);
                }
            }
            else
            {
                handler.emitError("Error renaming file", "File name "
                        + file.getName()
                        + " cannot be mapped using the expression \""
                        + pf.overrideRenameTo() + "\"");
            }
        }
    }

    protected void handleTimeStamp(PackFile pf, File file, File tmpFile)
    {
        // Set file modification time if specified
        if (pf.lastModified() >= 0)
        {
            if (blockableForCurrentOs(pf))
            {
                tmpFile.setLastModified(pf.lastModified());
            }
            else
            {
                file.setLastModified(pf.lastModified());
            }
        }
    }

    protected FileQueue handleBlockable(PackFile pf, File file, File tmpFile, FileQueue fq,
            List<InstallerListener> customActions)
    throws Exception
    {
        if (blockableForCurrentOs(pf))
        {
            if (fq == null)
            {
                fq = new FileQueue();
            }

            FileQueueMove fqmv = new FileQueueMove(tmpFile, file);
            if (blockableForCurrentOs(pf))
            {
                fqmv.setForceInUse(true);
            }
            fqmv.setOverwrite(true);
            fq.add(fqmv);
            Debug.log(tmpFile.getAbsolutePath()
                    + " -> "
                    + file.getAbsolutePath()
                    + " added to file queue for being copied after reboot"
            );
            // The temporary file must not be deleted
            // until the file queue will be committed
            tmpFile.deleteOnExit();
        }
        else
        {
            // Custom action listener stuff --- afterFile ----
            informListeners(customActions, InstallerListener.AFTER_FILE, file, pf,
                    null);
        }

        return fq;
    }

    protected void loadExecutables(ObjectInputStream objIn, ArrayList<ExecutableFile> executables)
    throws IOException, ClassNotFoundException
    {
        // Load information about executable files
        int numExecutables = objIn.readInt();
        for (int k = 0; k < numExecutables; k++)
        {
            ExecutableFile ef = (ExecutableFile) objIn.readObject();
            if (ef.hasCondition() && (rules != null))
            {
                if (!rules.isConditionTrue(ef.getCondition()))
                {
                    // skip, condition is false
                    continue;
                }
            }
            ef.path = IoHelper.translatePath(ef.path, variableSubstitutor);
            if (null != ef.argList && !ef.argList.isEmpty())
            {
                String arg = null;
                for (int j = 0; j < ef.argList.size(); j++)
                {
                    arg = ef.argList.get(j);
                    arg = IoHelper.translatePath(arg, variableSubstitutor);
                    ef.argList.set(j, arg);
                }
            }
            executables.add(ef);
            if (ef.executionStage == ExecutableFile.UNINSTALL)
            {
                udata.addExecutable(ef);
            }
        }
    }

}

