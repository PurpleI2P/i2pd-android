package org.purplei2p.i2pd;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.configuration2.INIConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;

public class MainPreferenceActivity extends PreferenceActivity {
    public static final String CONFIG_FILE_NAME = "i2pd/i2pd.conf";
    private static final String OLD_FILE_PREFIX = "__old";

    boolean isOldConfigExists() {
        try {
            return new File(getFilesDir(), CONFIG_FILE_NAME + OLD_FILE_PREFIX).exists();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_i2pd);
        final INIConfiguration properties = readConfiguration();
        if (!isOldConfigExists()) {
            File configFile = new File(getFilesDir(), CONFIG_FILE_NAME);
            File backupFile = new File(getFilesDir(), CONFIG_FILE_NAME + OLD_FILE_PREFIX);
            if (configFile.exists()) {
                try {
                    copyFile(configFile, backupFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        writeConfiguration(properties);
        initPreference("logLevelPreference", "log", "info", true, properties);
        initPreference("ipv4EnablePreference", "ipv4", "true", true, properties);
        initPreference("ipv6EnablePreference", "ipv6", "false", true, properties);
        EditTextPreference portPreference = (EditTextPreference) findPreference("portPreference");
        portPreference.setText(properties.getString("port", "auto"));
        portPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(MainPreferenceActivity.this, "For security reasons, changes are not allowed. If you really want this, open the config.", Toast.LENGTH_SHORT).show();
            return true;
        });
        initPreference("bandwidthPreference", "bandwidth", "L", true, properties);
        initPreference("noTransitPreference", "notransit", "false", true, properties);
        initPreference("floodfillPreference", "floodfill", "false", true, properties);
        initPreference("ssuPreference", "ssu2.enabled", "true", false, properties);
        initPreference("ntcp2EnablePreference", "ntcp2.enabled", "true", false, properties);
        initPreference("ntcp2PublishPreference", "ntcp2.published", "true", false, properties);
        initPreference("webConsoleEnablePreference", "http.enabled", "false", false, properties);
        initPreference("webConsoleAddressPreference", "http.address", "127.0.0.1", false, properties);
        initPreference("webConsolePortPreference", "http.port", "7070", false, properties);
        initPreference("webConsoleAuthPreference", "http.auth", "false", false, properties);
        initPreference("webConsoleUserPreference", "http.user", "user", false, properties);
        initPreference("webConsolePasswordPreference", "http.pass", "pass", false, properties);
        initPreference("httpProxyEnablePreference", "httpproxy.enabled", "true", false, properties);
        initPreference("httpProxyAddressPreference", "httpproxy.address", "127.0.0.1", false, properties);
        initPreference("httpProxyPortPreference", "httpproxy.port", "4444", false, properties);
        initPreference("httpProxyKeysPreference", "httpproxy.keys", "transient", false, properties);
        initPreference("socksProxyEnablePreference", "socksproxy.enabled", "true", false, properties);
        initPreference("socksProxyAddressPreference", "socksproxy.address", "127.0.0.1", false, properties);
        initPreference("socksProxyPortPreference", "socksproxy.port", "4447", false, properties);
        initPreference("socksProxyKeysPreference", "socksproxy.keys", "transient", false, properties);
        initPreference("samEnablePreference", "sam.enabled", "true", false, properties);
        initPreference("samAddressPreference", "sam.address", "127.0.0.1", false, properties);
        initPreference("samPortPreference", "sam.port", "7656", false, properties);
        initPreference("upnpEnablePreference", "upnp.enabled", "true", false, properties);
        initPreference("upnpForwardNamePreference", "upnp.name", "I2Pd", false, properties);
        initPreference("transitTunnelPreference", "limits.transittunnels", "3000", false, properties);
    }

    private void initPreference(String prefKey, final String propertyKey, final String defaultValue, final boolean useWriteConfig, final INIConfiguration properties) {
        Preference pref = findPreference(prefKey);
        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            listPref.setValue(properties.getString(propertyKey, defaultValue));
            listPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (useWriteConfig) {
                    writeConfiguration(properties, propertyKey, newValue.toString());
                } else {
                    properties.setProperty(propertyKey, newValue.toString());
                    writeConfiguration(properties);
                }
                return true;
            });
        } else if (pref instanceof CheckBoxPreference) {
            CheckBoxPreference checkPref = (CheckBoxPreference) pref;
            checkPref.setChecked(Boolean.parseBoolean(properties.getString(propertyKey, defaultValue)));
            checkPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (useWriteConfig) {
                    writeConfiguration(properties, propertyKey, String.valueOf((boolean) newValue));
                } else {
                    properties.setProperty(propertyKey, String.valueOf((boolean) newValue));
                    writeConfiguration(properties);
                }
                return true;
            });
        } else if (pref instanceof EditTextPreference) {
            EditTextPreference editPref = (EditTextPreference) pref;
            editPref.setText(properties.getString(propertyKey, defaultValue));
            editPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (useWriteConfig) {
                    writeConfiguration(properties, propertyKey, newValue.toString());
                } else {
                    properties.setProperty(propertyKey, newValue.toString());
                    writeConfiguration(properties);
                }
                return true;
            });
        }
    }

    private INIConfiguration readConfiguration() {
        INIConfiguration iniConfiguration = new INIConfiguration();
        File configFile = new File(getFilesDir(), CONFIG_FILE_NAME);
        try (FileReader reader = new FileReader(configFile)) {
            iniConfiguration.read(reader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return iniConfiguration;
    }

    private void writeConfiguration(INIConfiguration iniConfiguration) {
        writeConfiguration(iniConfiguration, "", "");
    }

    private void writeConfiguration(INIConfiguration iniConfiguration, String option, String value) {
        File configFile = new File(getFilesDir(), CONFIG_FILE_NAME);
        try (FileWriter writer = new FileWriter(configFile)) {
            StringWriter stringWriter = new StringWriter();
            iniConfiguration.write(stringWriter);
            String configFileContent = stringWriter.toString();
            String regexEmptySections = "(\\[\\w+\\]\\n(\\n|$)|\\[\\w+\\](\\z|\\Z))";
            configFileContent = configFileContent.replaceAll(regexEmptySections, "");
            if (!option.isEmpty()) {
                String optionPattern = option + "\\s+?=\\s+?\\w+";
                if (configFileContent.contains(option)) {
                    configFileContent = configFileContent.replaceAll(optionPattern, "");
                }
                configFileContent = option + "=" + value + "\n" + configFileContent;
            }
            Log.d("configFileContent", configFileContent);
            writer.write(configFileContent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }
}