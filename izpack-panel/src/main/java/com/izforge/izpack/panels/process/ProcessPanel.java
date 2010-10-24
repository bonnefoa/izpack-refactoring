/*
 * IzPack - Copyright 2001-2008 Julien Ponge, All Rights Reserved.
 *
 * http://izpack.org/
 * http://izpack.codehaus.org/
 *
 * Copyright 2004 Tino Schwarze
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

package com.izforge.izpack.panels.process;

import com.izforge.izpack.api.adaptator.IXMLElement;
import com.izforge.izpack.api.data.ResourceManager;
import com.izforge.izpack.api.handler.AbstractUIProcessHandler;
import com.izforge.izpack.installer.base.InstallerFrame;
import com.izforge.izpack.installer.base.IzPanel;
import com.izforge.izpack.installer.data.GUIInstallData;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * The process panel class.
 * <p/>
 * This class allows external processes to be executed during installation.
 * <p/>
 * Parts of the code have been taken from CompilePanel.java and modified a lot.
 *
 * @author Tino Schwarze
 * @author Julien Ponge
 */
public class ProcessPanel extends IzPanel implements AbstractUIProcessHandler
{

    /**
     *
     */
    private static final long serialVersionUID = 3258417209583155251L;

    /**
     * The operation label .
     */
    protected JLabel processLabel;

    /**
     * The overall progress bar.
     */
    protected JProgressBar overallProgressBar;

    /**
     * True if the compilation has been done.
     */
    private boolean validated = false;

    /**
     * The processing worker. Does all the work.
     */
    private ProcessPanelWorker worker;

    /**
     * Number of jobs to process. Used for progress indication.
     */
    private int noOfJobs = 0;

    private int currentJob = 0;

    /**
     * Where the output is displayed
     */
    private JTextArea outputPane;

    private static boolean finishedWork = false;

    /**
     * The constructor.
     *
     * @param processPanelWorker
     * @param parent             The parent window.
     * @param idata              The installation installDataGUI.
     */
    public ProcessPanel(InstallerFrame parent, GUIInstallData idata, ResourceManager resourceManager, ProcessPanelWorker processPanelWorker) throws IOException
    {
        super(parent, idata, resourceManager);

        this.worker = processPanelWorker;
        worker.setHandler(this);
        JLabel heading = new JLabel();
        Font font = heading.getFont();
        font = font.deriveFont(Font.BOLD, font.getSize() * 2.0f);
        heading.setFont(font);
        heading.setHorizontalAlignment(SwingConstants.CENTER);
        heading.setText(installData.getLangpack().getString("ProcessPanel.heading"));
        heading.setVerticalAlignment(SwingConstants.TOP);
        BorderLayout layout = new BorderLayout();
        layout.setHgap(2);
        layout.setVgap(2);
        setLayout(layout);
        add(heading, BorderLayout.NORTH);

        // put everything but the heading into it's own panel
        // (to center it vertically)
        JPanel subpanel = new JPanel();

        subpanel.setAlignmentX(0.5f);
        subpanel.setLayout(new BoxLayout(subpanel, BoxLayout.Y_AXIS));

        this.processLabel = new JLabel();
        this.processLabel.setAlignmentX(0.5f);
        this.processLabel.setText(" ");
        subpanel.add(this.processLabel);

        this.overallProgressBar = new JProgressBar();
        this.overallProgressBar.setAlignmentX(0.5f);
        this.overallProgressBar.setStringPainted(true);
        subpanel.add(this.overallProgressBar);

        this.outputPane = new JTextArea();
        this.outputPane.setEditable(false);
        JScrollPane outputScrollPane = new JScrollPane(this.outputPane);
        subpanel.add(outputScrollPane);

        add(subpanel, BorderLayout.CENTER);
    }

    /**
     * Indicates wether the panel has been validated or not.
     *
     * @return The validation state.
     */
    public boolean isValidated()
    {
        return validated;
    }

    /**
     * The compiler starts.
     */
    public void startProcessing(final int no_of_jobs)
    {
        this.noOfJobs = no_of_jobs;
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
              overallProgressBar.setMaximum(no_of_jobs);
              overallProgressBar.setIndeterminate(true);
              parent.lockPrevButton();
            }
        });
    }

    /**
     * The compiler stops.
     */
    public void finishProcessing(final boolean unlockPrev, final boolean unlockNext)
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                overallProgressBar.setIndeterminate(false);

                String no_of_jobs = Integer.toString(ProcessPanel.this.noOfJobs);
                overallProgressBar.setString(no_of_jobs + " / " + no_of_jobs);

                processLabel.setText(" ");
                processLabel.setEnabled(false);

                validated = true;
                ProcessPanel.this.installData.setInstallSuccess(worker.getResult());
                if (ProcessPanel.this.installData.getPanels().indexOf(ProcessPanel.this) != (ProcessPanel.this.installData.getPanels().size() - 1))
                {
                    if (unlockNext)
                    {
                        parent.unlockNextButton();
                    }
                }
                if (unlockPrev)
                {
                    parent.unlockPrevButton();
                }

                // set to finished only in case of success
                finishedWork = ProcessPanel.this.installData.isInstallSuccess();
            }
        });
    }

    /**
     * Log a message.
     *
     * @param message The message.
     * @param stderr  Whether the message came from stderr or stdout.
     */
    public void logOutput(String message, boolean stderr)
    {
        // TODO: make it colored
        this.outputPane.append(message + '\n');

        SwingUtilities.invokeLater(new Runnable()
        {

            public void run()
            {
                outputPane.setCaretPosition(outputPane.getText().length());
            }
        });
    }

    /**
     * Next job starts.
     *
     * @param jobName The job name.
     */
    public void startProcess(final String jobName)
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                processLabel.setText(jobName);

                ProcessPanel.this.currentJob++;
                overallProgressBar.setValue(ProcessPanel.this.currentJob);
                overallProgressBar.setString(String.valueOf(ProcessPanel.this.currentJob) + " / "
                        + String.valueOf(ProcessPanel.this.noOfJobs));
            }
         });
    }

    public void finishProcess()
    {

    }

    /**
     * Called when the panel becomes active.
     */
    public void panelActivate()
    {
        // We clip the panel
        Dimension dimension = parent.getPanelsContainerSize();
        dimension.width -= (dimension.width / 4);
        dimension.height = 150;
        setMinimumSize(dimension);
        setMaximumSize(dimension);
        setPreferredSize(dimension);

        parent.lockNextButton();

        // only let the process start if the weren't finished before.
        if (!finishedWork)
        {
            this.worker.startThread();
        }
    }

    /**
     * Create XML installDataGUI for automated installation.
     */
    public void makeXMLData(IXMLElement panelRoot)
    {
        // does nothing (no state to save)
    }

}
