package com.izforge.izpack.installer.container.provider;

import com.izforge.izpack.api.container.BindeableContainer;
import com.izforge.izpack.api.data.GUIPrefs;
import com.izforge.izpack.api.data.ResourceManager;
import com.izforge.izpack.api.substitutor.VariableSubstitutor;
import com.izforge.izpack.gui.ButtonFactory;
import com.izforge.izpack.gui.LabelFactory;
import com.izforge.izpack.installer.data.GUIInstallData;
import com.izforge.izpack.merge.resolve.ClassPathCrawler;
import com.izforge.izpack.merge.resolve.PathResolver;
import com.izforge.izpack.util.Debug;
import com.izforge.izpack.util.OsVersion;

import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;
import java.awt.*;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Provide installData for GUI :
 * Load install data with l&f and GUIPrefs
 */
public class GUIInstallDataProvider extends AbstractInstallDataProvider
{

    public GUIInstallData provide(ResourceManager resourceManager, VariableSubstitutor variableSubstitutor, Properties variables, PathResolver pathResolver, ClassPathCrawler classPathCrawler, BindeableContainer container) throws Exception
    {
        this.resourceManager = resourceManager;
        this.variableSubstitutor = variableSubstitutor;
        this.classPathCrawler = classPathCrawler;
        final GUIInstallData guiInstallData = new GUIInstallData(variables, variableSubstitutor);
        // Loads the installation data
        loadInstallData(guiInstallData);
        // Load custom action data.
//        loadCustomData(guiInstallData, container, pathResolver);

        loadGUIInstallData(guiInstallData);
        loadInstallerRequirements(guiInstallData);
        loadDynamicVariables(guiInstallData);
        // Load custom langpack if exist.
        addCustomLangpack(guiInstallData);
        loadDefaultLocale(guiInstallData);
        loadLookAndFeel(guiInstallData);
        return guiInstallData;
    }

    /**
     * Loads the suitable L&F.
     *
     * @param installdata
     * @throws Exception Description of the Exception
     */
    protected void loadLookAndFeel(GUIInstallData installdata) throws Exception
    {
        // Do we have any preference for this OS ?
        String syskey = "unix";
        if (OsVersion.IS_WINDOWS)
        {
            syskey = "windows";
        }
        else if (OsVersion.IS_OSX)
        {
            syskey = "mac";
        }
        String lookAndFeelName = null;
        if (installdata.guiPrefs.lookAndFeelMapping.containsKey(syskey))
        {
            lookAndFeelName = installdata.guiPrefs.lookAndFeelMapping.get(syskey);
        }

        // Let's use the system LAF
        // Resolve whether button icons should be used or not.
        boolean useButtonIcons = true;
        if (installdata.guiPrefs.modifier.containsKey("useButtonIcons")
                && "no".equalsIgnoreCase(installdata.guiPrefs.modifier
                .get("useButtonIcons")))
        {
            useButtonIcons = false;
        }
        ButtonFactory.useButtonIcons(useButtonIcons);
        boolean useLabelIcons = true;
        if (installdata.guiPrefs.modifier.containsKey("useLabelIcons")
                && "no".equalsIgnoreCase(installdata.guiPrefs.modifier
                .get("useLabelIcons")))
        {
            useLabelIcons = false;
        }
        LabelFactory.setUseLabelIcons(useLabelIcons);
        if (installdata.guiPrefs.modifier.containsKey("labelFontSize"))
        {  //'labelFontSize' modifier found in 'guiprefs'
            final String valStr =
                    installdata.guiPrefs.modifier.get("labelFontSize");
            try
            {      //parse value and enter as label-font-size multiplier:
                LabelFactory.setLabelFontSize(Float.parseFloat(valStr));
            }
            catch (NumberFormatException ex)
            {      //error parsing value; log message
                Debug.log("Error parsing guiprefs 'labelFontSize' value (" +
                        valStr + ')');
            }
        }

        if (lookAndFeelName == null)
        {
            if (!"mac".equals(syskey))
            {
                // In Linux we will use the English locale, because of a bug in
                // JRE6. In Korean, Persian, Chinese, japanese and some other
                // locales the installer throws and exception and doesn't load
                // at all. See http://jira.jboss.com/jira/browse/JBINSTALL-232.
                // This is a workaround until this bug gets fixed.
                if ("unix".equals(syskey))
                {
                    Locale.setDefault(Locale.ENGLISH);
                }
                String syslaf = UIManager.getSystemLookAndFeelClassName();
                UIManager.setLookAndFeel(syslaf);
                if (UIManager.getLookAndFeel() instanceof MetalLookAndFeel)
                {
                    ButtonFactory.useButtonIcons(useButtonIcons);
                }
            }
            return;
        }

        // Kunststoff (http://www.incors.org/)
        if ("kunststoff".equals(lookAndFeelName))
        {
            ButtonFactory.useHighlightButtons();
            // Reset the use button icons state because useHighlightButtons
            // make it always true.
            ButtonFactory.useButtonIcons(useButtonIcons);
            installdata.buttonsHColor = new Color(255, 255, 255);
            Class<LookAndFeel> lafClass = (Class<LookAndFeel>) Class.forName("com.incors.plaf.kunststoff.KunststoffLookAndFeel");
            Class mtheme = Class.forName("javax.swing.plaf.metal.MetalTheme");
            Class[] params = {mtheme};
            Class<MetalTheme> theme = (Class<MetalTheme>) Class.forName("com.izforge.izpack.gui.IzPackKMetalTheme");
            Method setCurrentThemeMethod = lafClass.getMethod("setCurrentTheme", params);

            // We invoke and place Kunststoff as our L&F
            LookAndFeel kunststoff = lafClass.newInstance();
            MetalTheme ktheme = theme.newInstance();
            Object[] kparams = {ktheme};
            UIManager.setLookAndFeel(kunststoff);
            setCurrentThemeMethod.invoke(kunststoff, kparams);
            return;
        }

        // Liquid (http://liquidlnf.sourceforge.net/)
        if ("liquid".equals(lookAndFeelName))
        {
            UIManager.setLookAndFeel("com.birosoft.liquid.LiquidLookAndFeel");

            Map<String, String> params = installdata.guiPrefs.lookAndFeelParams.get(lookAndFeelName);
            if (params.containsKey("decorate.frames"))
            {
                String value = params.get("decorate.frames");
                if ("yes".equals(value))
                {
                    JFrame.setDefaultLookAndFeelDecorated(true);
                }
            }
            if (params.containsKey("decorate.dialogs"))
            {
                String value = params.get("decorate.dialogs");
                if ("yes".equals(value))
                {
                    JDialog.setDefaultLookAndFeelDecorated(true);
                }
            }

            return;
        }

        // Metouia (http://mlf.sourceforge.net/)
        if ("metouia".equals(lookAndFeelName))
        {
            UIManager.setLookAndFeel("net.sourceforge.mlf.metouia.MetouiaLookAndFeel");
            return;
        }

        // Nimbus (http://nimbus.dev.java.net/)
        if ("nimbus".equals(lookAndFeelName))
        {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            return;
        }

        // JGoodies Looks (http://looks.dev.java.net/)
        if ("looks".equals(lookAndFeelName))
        {
            Map<String, String> variants = new TreeMap<String, String>();
            variants.put("windows", "com.jgoodies.looks.windows.WindowsLookAndFeel");
            variants.put("plastic", "com.jgoodies.looks.plastic.PlasticLookAndFeel");
            variants.put("plastic3D", "com.jgoodies.looks.plastic.Plastic3DLookAndFeel");
            variants.put("plasticXP", "com.jgoodies.looks.plastic.Plastic3DLookAndFeel");
            String variant = variants.get("plasticXP");

            Map<String, String> params = installdata.guiPrefs.lookAndFeelParams.get(lookAndFeelName);
            if (params.containsKey("variant"))
            {
                String param = params.get("variant");
                if (variants.containsKey(param))
                {
                    variant = variants.get(param);
                }
            }

            UIManager.setLookAndFeel(variant);
            return;
        }

        // Substance (http://substance.dev.java.net/)
        if ("substance".equals(lookAndFeelName))
        {
            Map<String, String> variants = new TreeMap<String, String>();
            variants.put("default", "org.pushingpixels.substance.SubstanceLookAndFeel"); // Ugly!!!
            variants.put("business", "org.pushingpixels.substance.api.skin.SubstanceBusinessLookAndFeel");
            variants.put("business-blue", "org.pushingpixels.substance.api.skin.SubstanceBusinessBlueSteelLookAndFeel");
            variants.put("business-black", "org.pushingpixels.substance.api.skin.SubstanceBusinessBlackSteelLookAndFeel");
            variants.put("creme", "org.pushingpixels.substance.api.skin.SubstanceCremeLookAndFeel");
            variants.put("creme-coffee", "org.pushingpixels.substance.api.skin.SubstanceCremeCoffeeLookAndFeel");
            variants.put("sahara", "org.pushingpixels.substance.api.skin.SubstanceSaharaLookAndFeel");
            variants.put("moderate", "org.pushingpixels.substance.api.skin.SubstanceModerateLookAndFeel");
            variants.put("nebula", "org.pushingpixels.substance.api.skin.SubstanceNebulaLookAndFeel");
            variants.put("nebula-brick-wall", "org.pushingpixels.substance.api.skin.SubstanceNebulaBrickWallLookAndFeel");
            variants.put("autumn", "org.pushingpixels.substance.api.skin.SubstanceAutumnLookAndFeel");
            variants.put("mist-silver", "org.pushingpixels.substance.api.skin.SubstanceMistSilverLookAndFeel");
            variants.put("mist-aqua", "org.pushingpixels.substance.api.skin.SubstanceMistAquaLookAndFeel");
            variants.put("dust", "org.pushingpixels.substance.api.skin.SubstanceDustLookAndFeel");
            variants.put("dust-coffee", "org.pushingpixels.substance.api.skin.SubstanceDustCoffeeLookAndFeel");
            variants.put("gemini", "org.pushingpixels.substance.api.skin.SubstanceGeminiLookAndFeel");
            variants.put("mariner", "org.pushingpixels.substance.api.skin.SubstanceMarinerLookAndFeel");
            variants.put("officesilver", "org.pushingpixels.substance.skin.SubstanceOfficeSilver2007LookAndFeel");
            variants.put("officeblue", "org.pushingpixels.substance.api.skin.SubstanceOfficeBlue2007LookAndFeel");
            variants.put("officeblack", "org.pushingpixels.substance.api.skin.SubstanceOfficeBlack2007LookAndFeel");
            String variant = variants.get("default");

            Map<String, String> params = installdata.guiPrefs.lookAndFeelParams.get(lookAndFeelName);
            if (params.containsKey("variant"))
            {
                String param = params.get("variant");
                if (variants.containsKey(param))
                {
                    variant = variants.get(param);
                }
            }
            UIManager.setLookAndFeel(variant);
        }
    }

    /**
     * Load GUI preference information.
     *
     * @param installdata
     * @throws Exception
     */
    private void loadGUIInstallData(GUIInstallData installdata) throws Exception
    {
        InputStream in = resourceManager.getInputStream("GUIPrefs");
        ObjectInputStream objIn = new ObjectInputStream(in);
        installdata.guiPrefs = (GUIPrefs) objIn.readObject();
        objIn.close();
    }


}
