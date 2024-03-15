package org.purplei2p.i2pd;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import android.util.Log;
import java.io.File;
import org.apache.commons.configuration2.INIConfiguration;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.*;

public class MainPreferenceActivity extends PreferenceActivity {
    private Boolean isOldConfigExists = false;
    public static final String CONFIG_FILE_PATH = "/sdcard/i2pd/i2pd.conf";
    private static final String OLD_FILE_PREFIX= "__old";

    boolean isOldConfigExists()
    {
        try {
            return new File(CONFIG_FILE_PATH + OLD_FILE_PREFIX).exists();
        }catch(Exception e) { return false; }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_i2pd);
        INIConfiguration properties = readConfiguration();
        // backup old configuration.
        if (!isOldConfigExists)
        {
            try {
                if (Files.exists(Paths.get(CONFIG_FILE_PATH))) {
                    Files.copy(Paths.get(CONFIG_FILE_PATH), Paths.get(CONFIG_FILE_PATH + OLD_FILE_PREFIX));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // delete empty sections
        {
            writeConfiguration(properties);
        }
        // Main Categoryре
        ListPreference logLevel = (ListPreference) findPreference("logLevelPreference");
        logLevel.setValue(properties.getString("log", "info"));
        logLevel.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            //properties.setProperty("log", (String) newValue);
            // Save modified properties
            //writeConfiguration(properties);
            writeConfiguration(properties, "log", (String) newValue);
            return true;
        });

        CheckBoxPreference ipv4Enable = (CheckBoxPreference) findPreference("ipv4EnablePreference");
        boolean ipv4Enabled = Boolean.parseBoolean(properties.getString("ipv4", "true")); // "true" - значение по умолчанию, если ключ отсутствует
        ipv4Enable.setChecked(ipv4Enabled);
        ipv4Enable.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            // Convert Object to boolean
            boolean newValueBoolean = (boolean) newValue;
            //properties.setProperty("ipv4", String.valueOf(newValueBoolean)); // assuming "ipv4" is the key
            // Save modified properties
            writeConfiguration(properties, "ipv4", String.valueOf(newValueBoolean));
            return true;
        });
        CheckBoxPreference ipv6Enable = (CheckBoxPreference) findPreference("ipv6EnablePreference");
        boolean ipv6Enabled = Boolean.parseBoolean(properties.getString("ipv6", "false"));
        ipv6Enable.setChecked(ipv6Enabled);
        ipv6Enable.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            // Convert Object to boolean
            boolean newValueBoolean = (boolean) newValue;
            //properties.setProperty("ipv6", String.valueOf(newValueBoolean)); // assuming "ipv4" is the key
            // Save modified properties
            writeConfiguration(properties, "ipv6", String.valueOf(newValueBoolean));
            return true;
        });
// Example for portPreference (EditTextPreference)
        EditTextPreference portPreference = (EditTextPreference) findPreference("portPreference");
        String portValue = properties.getString("port", "auto");
        portPreference.setText(portValue);
        portPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            /*Properties properties = readProperties();
            // Convert Object to String (assuming "port" is the key)
            String newValueString = newValue.toString();
            properties.setProperty("port", newValueString);
            // Save modified properties
            writeProperties(properties);*/
            Toast.makeText(MainPreferenceActivity.this, "For security reasons, changes are not allowed. If you really want this, open the config.", Toast.LENGTH_SHORT).show();

            return true;
        });

        // Example for bandwidthPreference (ListPreference)
        ListPreference bandwidthPreference = (ListPreference) findPreference("bandwidthPreference");
        String bandwidthValue = properties.getString("bandwidth", "L");
        bandwidthPreference.setValue(bandwidthValue);
        bandwidthPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            //properties.setProperty("bandwidth", newValueString);
            writeConfiguration(properties, "bandwidth", newValueString);
            return true;
        });
// Example for noTransitPreference (CheckBoxPreference)
        CheckBoxPreference noTransitPreference = (CheckBoxPreference) findPreference("noTransitPreference");
        boolean noTransitPreferenceEnabled = Boolean.parseBoolean(properties.getString("notransit", "false"));
        noTransitPreference.setChecked(noTransitPreferenceEnabled);

        noTransitPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;
            //properties.setProperty("notransit", String.valueOf(newValueBoolean));
            writeConfiguration(properties, "notransit", String.valueOf(newValueBoolean));
            return true;
        });
        CheckBoxPreference floodfillPreference = (CheckBoxPreference) findPreference("floodfillPreference");
        boolean floodfillPreferenceEnabled = Boolean.parseBoolean(properties.getString("floodfill", "false"));
        floodfillPreference.setChecked(floodfillPreferenceEnabled);
        floodfillPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;
            //properties.setProperty("floodfill", String.valueOf(newValueBoolean));
            writeConfiguration(properties, "floodfill", String.valueOf(newValueBoolean));
            return true;
        });
        // ^^^ general
        // vvv not general (sections name)
        CheckBoxPreference ssuPreference = (CheckBoxPreference) findPreference("ssuPreference");
        boolean ssuPreferenceEnabled = Boolean.parseBoolean(properties.getString("ssu2.enabled", "true"));
        ssuPreference.setChecked(ssuPreferenceEnabled);
        ssuPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;

            properties.setProperty("ssu2.enabled", String.valueOf(newValueBoolean));
            writeConfiguration(properties);

            return true; // Allow the change
        });
        // NTCP2 Category
        CheckBoxPreference ntcp2Enable = (CheckBoxPreference) findPreference("ntcp2EnablePreference");

        boolean ntcp2EnabledPreference = Boolean.parseBoolean(properties.getString("ntcp2.enabled", "true"));
        ntcp2Enable.setChecked(ntcp2EnabledPreference);


        ntcp2Enable.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;

            properties.setProperty("ntcp2.enabled", String.valueOf(newValueBoolean));
            writeConfiguration(properties);

            return true; // Allow the change
        });
        CheckBoxPreference ntcp2Publish = (CheckBoxPreference) findPreference("ntcp2PublishPreference");

        boolean ntcp2PublishEnabledPreference = Boolean.parseBoolean(properties.getString("ntcp2.published", "true"));
        ntcp2Publish.setChecked(ntcp2EnabledPreference);


        ntcp2Publish.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;

            properties.setProperty("ntcp2.published", String.valueOf(newValueBoolean));
            writeConfiguration(properties);

            return true; // Allow the change
        });
        // Web Console Category
        CheckBoxPreference webConsoleEnable = (CheckBoxPreference) findPreference("webConsoleEnablePreference");
        boolean webConsoleEnableValue = Boolean.parseBoolean(properties.getString("http.enabled", "false"));
        webConsoleEnable.setChecked(webConsoleEnableValue);

        webConsoleEnable.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;
            properties.setProperty("http.enabled", String.valueOf(newValueBoolean));
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference webConsoleAddress = (EditTextPreference) findPreference("webConsoleAddressPreference");
        String webConsoleAddressValue = properties.getString("http.address", "127.0.0.1");
        webConsoleAddress.setText(webConsoleAddressValue);
        webConsoleAddress.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("http.address", newValueString);
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference webConsolePort = (EditTextPreference) findPreference("webConsolePortPreference");
        String webConsolePortValue = properties.getString("http.port", "7070");
        webConsolePort.setText(webConsolePortValue);
        webConsolePort.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("http.port", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        CheckBoxPreference webConsoleAuth = (CheckBoxPreference) findPreference("webConsoleAuthPreference");
        boolean webConsoleAuthValue = Boolean.parseBoolean(properties.getString("http.auth", "false"));
        webConsoleAuth.setChecked(webConsoleAuthValue);
        webConsoleAuth.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;
            properties.setProperty("http.auth", String.valueOf(newValueBoolean));
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference webConsoleUser = (EditTextPreference) findPreference("webConsoleUserPreference");
        String webConsoleUserValue = properties.getString("http.user", "user");
        webConsoleUser.setText(webConsoleUserValue);
        webConsoleUser.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("http.user", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference webConsolePassword = (EditTextPreference) findPreference("webConsolePasswordPreference");
        String webConsolePasswordValue = properties.getString("http.pass", "pass");
        webConsolePassword.setText(webConsolePasswordValue);

        webConsolePassword.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("http.pass", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        // HTTP Proxy Category
        CheckBoxPreference httpProxyEnable = (CheckBoxPreference) findPreference("httpProxyEnablePreference");
        boolean httpProxyEnabled = Boolean.parseBoolean(properties.getString("httpproxy.enabled", "true"));
        httpProxyEnable.setChecked(httpProxyEnabled);
        httpProxyEnable.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;
            properties.setProperty("httpproxy.enabled", String.valueOf(newValueBoolean));
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference httpProxyAddress = (EditTextPreference) findPreference("httpProxyAddressPreference");
        String httpProxyAddressValue = properties.getString("httpproxy.address", "127.0.0.1");
        httpProxyAddress.setText(httpProxyAddressValue);

        httpProxyAddress.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("httpproxy.address", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference httpProxyPort = (EditTextPreference) findPreference("httpProxyPortPreference");
        String httpProxyPortValue = properties.getString("httpproxy.port", "4444");
        httpProxyPort.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("httpproxy.port", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference httpProxyKeys = (EditTextPreference) findPreference("httpProxyKeysPreference");
        String httpProxyKeyValue = properties.getString("httpproxy.keys", "transient");
        httpProxyKeys.setText(httpProxyKeyValue);
        httpProxyKeys.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("httpproxy.keys", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });

        // SOCKS Proxy Category
        CheckBoxPreference socksProxyEnable = (CheckBoxPreference) findPreference("socksProxyEnablePreference");
        boolean socksProxyEnabled = Boolean.parseBoolean(properties.getString("socksproxy.enabled", "true"));
        socksProxyEnable.setChecked(socksProxyEnabled);
        socksProxyEnable.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;
            properties.setProperty("socksproxy.enabled", String.valueOf(newValueBoolean));
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference socksProxyAddress = (EditTextPreference) findPreference("socksProxyAddressPreference");
        String socksProxyAddressValue = properties.getString("socksproxy.address", "127.0.0.1");
        socksProxyAddress.setText(socksProxyAddressValue);
        socksProxyAddress.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("socksproxy.address", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference socksProxyPort = (EditTextPreference) findPreference("socksProxyPortPreference");
        String socksProxyPortValue = properties.getString("socksproxy.port", "4447");
        socksProxyPort.setText(socksProxyPortValue);
        socksProxyPort.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("socksproxy.port", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference socksProxyKeys = (EditTextPreference) findPreference("socksProxyKeysPreference");
        String socksProxyKeysValue = properties.getString("socksproxy.keys", "transient");
        socksProxyKeys.setText(socksProxyKeysValue);
        socksProxyKeys.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("socksproxy.keys", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        // SAM Category
        CheckBoxPreference samEnable = (CheckBoxPreference) findPreference("samEnablePreference");
        boolean samEnableValue = Boolean.parseBoolean(properties.getString("sam.enabled", "true"));
        samEnable.setChecked(samEnableValue);
        samEnable.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;
            properties.setProperty("sam.enabled", String.valueOf(newValueBoolean));
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference samAddress = (EditTextPreference) findPreference("samAddressPreference");
        String samAddressValue = properties.getString("sam.address", "127.0.0.1");
        samAddress.setText(samAddressValue);
        samAddress.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("sam.address", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference samPort = (EditTextPreference) findPreference("samPortPreference");
        String samPortValue = properties.getString("sam.port", "7656");
        samPort.setText(samPortValue);
        samPort.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("sam.port", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        // UPnP Category
        CheckBoxPreference upnpEnable = (CheckBoxPreference) findPreference("upnpEnablePreference");
        boolean upnpEnableValue = Boolean.parseBoolean(properties.getString("upnp.enabled", "true"));
        upnpEnable.setChecked(upnpEnableValue);
        upnpEnable.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newValueBoolean = (boolean) newValue;
            properties.setProperty("upnp.enabled", String.valueOf(newValueBoolean));
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        EditTextPreference upnpForwardName = (EditTextPreference) findPreference("upnpForwardNamePreference");
        String upnpForwardNameValue = properties.getString("upnp.name", "I2Pd");
        upnpForwardName.setText(upnpForwardNameValue);
        upnpForwardName.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("upnp.name", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
        // Limits Category
        EditTextPreference transitTunnelEdit = (EditTextPreference) findPreference("transitTunnelPreference");
        String transitTunnelValue = properties.getString("limits.transittunnels", "3000");
        transitTunnelEdit.setText(transitTunnelValue);
        transitTunnelEdit.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            properties.setProperty("limits.transittunnels", newValueString);
            // Save modified properties
            writeConfiguration(properties);
            return true;
        });
    }
/*
    private Properties readProperties() {
        if (isOldConfigExists) {
            Toast.makeText(this, oldConfigErrMsg, Toast.LENGTH_SHORT).show();
        }
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(CONFIG_FILE_PATH)) {
            properties.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties;
    }
*/
    /*
    private void writeProperties(Properties properties) {
        if (isOldConfigExists) {
            Toast.makeText(this, oldConfigErrMsg, Toast.LENGTH_SHORT).show();
        } else {
            try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH)) {
                properties.store(writer, "Updated properties");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }*/

    private INIConfiguration readConfiguration() {
        INIConfiguration iniConfiguration = new INIConfiguration();
        try (FileReader reader = new FileReader(CONFIG_FILE_PATH)) {
            iniConfiguration.read(reader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return iniConfiguration;
    }
    private void writeConfiguration(INIConfiguration iniConfiguration)
    {
        writeConfiguration(iniConfiguration, "", "");
    }
    private void writeConfiguration(INIConfiguration iniConfiguration, String option, String value) {
        try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH)) {
            StringWriter stringWriter = new StringWriter();
            iniConfiguration.write(stringWriter);
            String configFileContent = stringWriter.toString();

            // Удаление пустых секций из строки конфигурации
            String regexEmptySections = "(\\[\\w+\\]\\n(\\n|$)|\\[\\w+\\](\\z|\\Z))";
            configFileContent = configFileContent.replaceAll(regexEmptySections, "");

            // Если указана опция для перемещения
            if (!option.isEmpty()) {
                Log.d("configFileContent","option is not empty");
                String optionPattern = option + "\\s+?=\\s+?\\w+"; // Паттерн для поиска опции с присваиванием в начале строки
                Log.d("confiFileContent","config File contains option");
                if (configFileContent.contains(option))  configFileContent = configFileContent.replaceAll(optionPattern, ""); // Удаляем первое вхождение
                Log.d("confiFileContent", option);
                Log.d("confiFileContent", value);
                Log.d("confiFileContent","Add on start line");
                configFileContent = option + "=" + value + "\n" + configFileContent;

            }
            Log.d("configFileContent", configFileContent);
            writer.write(configFileContent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
