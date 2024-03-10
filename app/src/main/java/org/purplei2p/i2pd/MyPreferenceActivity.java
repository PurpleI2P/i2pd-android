package org.purplei2p.i2pd;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class MyPreferenceActivity extends PreferenceActivity {

    private static final String CONFIG_FILE_PATH = "/sdcard/i2pd/i2pd.conf";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_i2pd);

        // Main Category
        ListPreference logLevel = (ListPreference) findPreference("logLevelPreference");

        logLevel.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            Properties properties = readProperties();
            properties.setProperty("log", (String) newValue);
            // Save modified properties
            writeProperties(properties);
            return true;
        });

        CheckBoxPreference ipv4Enable = (CheckBoxPreference) findPreference("ipv4EnablePreference");
        ipv4Enable.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            Properties properties = readProperties();
            // Convert Object to boolean
            boolean newValueBoolean = (boolean) newValue;
            properties.setProperty("ipv4", String.valueOf(newValueBoolean)); // assuming "ipv4" is the key
            // Save modified properties
            writeProperties(properties);
            return true;
        });
        CheckBoxPreference ipv6Enable = (CheckBoxPreference) findPreference("ipv6EnablePreference");
        ipv6Enable.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            Properties properties = readProperties();
            // Convert Object to boolean
            boolean newValueBoolean = (boolean) newValue;
            properties.setProperty("ipv6", String.valueOf(newValueBoolean)); // assuming "ipv4" is the key
            // Save modified properties
            writeProperties(properties);
            return true;
        });
// Example for portPreference (EditTextPreference)
        EditTextPreference portPreference = (EditTextPreference) findPreference("portPreference");

        portPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            Properties properties = readProperties();
            // Convert Object to String (assuming "port" is the key)
            String newValueString = newValue.toString();
            properties.setProperty("port", newValueString);
            // Save modified properties
            writeProperties(properties);
            return true;
        });

        // Example for bandwidthPreference (ListPreference)
        ListPreference bandwidthPreference = (ListPreference) findPreference("bandwidthPreference");

        bandwidthPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            Properties properties = readProperties();
            // Convert Object to String (assuming "bandwidth" is the key)
            String newValueString = newValue.toString();
            properties.setProperty("bandwidth", newValueString);
            // Save modified properties
            writeProperties(properties);
            return true;
        });
// Example for noTransitPreference (CheckBoxPreference)
        CheckBoxPreference noTransitPreference = (CheckBoxPreference) findPreference("noTransitPreference");

        noTransitPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            Properties properties = readProperties();
            // Convert Object to boolean (assuming "noTransit" is the key)
            boolean newValueBoolean = (boolean) newValue;
            properties.setProperty("notransit", String.valueOf(newValueBoolean));
            // Save modified properties
            writeProperties(properties);
            return true;
        });
        CheckBoxPreference floodfillPreference = (CheckBoxPreference) findPreference("floodfillPreference");
        floodfillPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            // Modify properties
            Properties properties = readProperties();
            // Convert Object to boolean (assuming "noTransit" is the key)
            boolean newValueBoolean = (boolean) newValue;
            properties.setProperty("floodfill", String.valueOf(newValueBoolean));
            // Save modified properties
            writeProperties(properties);
            return true;
        });
        // ^^^ general
        // vvv not general (sections name)
        CheckBoxPreference ssuPreference = (CheckBoxPreference) findPreference("ssuPreference");

        // NTCP2 Category
        CheckBoxPreference ntcp2Enable = (CheckBoxPreference) findPreference("ntcp2EnablePreference");
        CheckBoxPreference ntcp2Publish = (CheckBoxPreference) findPreference("ntcp2PublishPreference");

        // Web Console Category
        CheckBoxPreference webConsoleEnable = (CheckBoxPreference) findPreference("webConsoleEnablePreference");
        EditTextPreference webConsoleAddress = (EditTextPreference) findPreference("webConsoleAddressPreference");
        EditTextPreference webConsolePort = (EditTextPreference) findPreference("webConsolePortPreference");
        CheckBoxPreference webConsoleAuth = (CheckBoxPreference) findPreference("webConsoleAuthPreference");
        EditTextPreference webConsoleUser = (EditTextPreference) findPreference("webConsoleUserPreference");
        EditTextPreference webConsolePassword = (EditTextPreference) findPreference("webConsolePasswordPreference");

        // HTTP Proxy Category
        CheckBoxPreference httpProxyEnable = (CheckBoxPreference) findPreference("httpProxyEnablePreference");
        EditTextPreference httpProxyAddress = (EditTextPreference) findPreference("httpProxyAddressPreference");
        EditTextPreference httpProxyPort = (EditTextPreference) findPreference("httpProxyPortPreference");
        EditTextPreference httpProxyKeys = (EditTextPreference) findPreference("httpProxyKeysPreference");

        // SOCKS Proxy Category
        CheckBoxPreference socksProxyEnable = (CheckBoxPreference) findPreference("socksProxyEnablePreference");
        EditTextPreference socksProxyAddress = (EditTextPreference) findPreference("socksProxyAddressPreference");
        EditTextPreference socksProxyPort = (EditTextPreference) findPreference("socksProxyPortPreference");
        EditTextPreference socksProxyKeys = (EditTextPreference) findPreference("socksProxyKeysPreference");

        // SAM Category
        CheckBoxPreference samEnable = (CheckBoxPreference) findPreference("samEnablePreference");
        EditTextPreference samAddress = (EditTextPreference) findPreference("samAddressPreference");
        EditTextPreference samPort = (EditTextPreference) findPreference("samPortPreference");

        // UPnP Category
        CheckBoxPreference upnpEnable = (CheckBoxPreference) findPreference("upnpEnablePreference");
        EditTextPreference upnpForwardName = (EditTextPreference) findPreference("upnpForwardNamePreference");

        // Limits Category
        EditTextPreference transitTunnelEdit = (EditTextPreference) findPreference("transitTunnelPreference");
    }

    private Properties readProperties() {
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(CONFIG_FILE_PATH)) {
            properties.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties;
    }

    private void writeProperties(Properties properties) {
        try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH)) {
            properties.store(writer, "Updated properties");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
