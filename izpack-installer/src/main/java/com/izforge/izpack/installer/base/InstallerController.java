package com.izforge.izpack.installer.base;

import com.izforge.izpack.api.data.AutomatedInstallData;
import com.izforge.izpack.installer.manager.PanelManager;

import javax.swing.*;

/**
 * Installer frame controller
 *
 * @author Anthonin Bonnefoy
 */
public class InstallerController
{

    private InstallerFrame installerFrame;
    private PanelManager panelManager;
    private AutomatedInstallData automatedInstallData;

    public InstallerController(PanelManager panelManager, InstallDataConfiguratorWithRules installDataRulesEngineManager, InstallerFrame installerFrame, AutomatedInstallData automatedInstallData)
    {

        this.panelManager = panelManager;
        this.installerFrame = installerFrame;
        this.automatedInstallData = automatedInstallData;
        installDataRulesEngineManager.configureInstallData();

    }

    public InstallerController buildInstallation() throws Exception
    {
        panelManager.loadPanelsInContainer();
        panelManager.instantiatePanels();
       SwingUtilities.invokeLater(new Runnable() {
                public void run()
                {
                  installerFrame.buildGUI();
                  installerFrame.sizeFrame();
                }
        });
        return this;
    }

    public void launchInstallation()
    {
        activateFirstPanel();
    }

    private void activateFirstPanel()
    {
        int firstPanel = installerFrame.hasNavigateNext(-1, false);
        if (firstPanel > -1)
        {
            installerFrame.setVisible(true);
            automatedInstallData.setCurPanelNumber(firstPanel);
            installerFrame.switchPanel(firstPanel);
        }
    }

}
