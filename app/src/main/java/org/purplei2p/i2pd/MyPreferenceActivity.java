package org.purplei2p.i2pd;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

public class MyPreferenceActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_i2pd);

        // Main Category
        ListPreference logLevel = (ListPreference) findPreference("logLevelPreference");
        CheckBoxPreference ipv4Enable = (CheckBoxPreference) findPreference("ipv4EnablePreference");
        CheckBoxPreference ipv6Enable = (CheckBoxPreference) findPreference("ipv6EnablePreference");
        EditTextPreference portPreference = (EditTextPreference) findPreference("portPreference");
        ListPreference bandwidthPreference = (ListPreference) findPreference("bandwidthPreference");
        CheckBoxPreference noTransitPreference = (CheckBoxPreference) findPreference("noTransitPreference");
        CheckBoxPreference floodfillPreference = (CheckBoxPreference) findPreference("floodfillPreference");
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
}
