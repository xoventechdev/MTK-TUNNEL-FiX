/*
 * Copyright (c) 2013, Kevin Cernekee
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * In addition, as a special exception, the copyright holders give
 * permission to link the code of portions of this program with the
 * OpenSSL library.
 */

package app.openconnect.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import app.openconnect.OcsVpnProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.preference.PreferenceManager;
import android.util.Log;

public class ProfileManager {
	public static final String TAG = "OpenConnect";

	public static String fileSelectKeys[] =
		{ "ca_certificate", "user_certificate", "private_key", "custom_csd_wrapper" };

	private static final String PROFILE_PFX = "profile-";
	private static HashMap<String, OcsVpnProfile> mProfiles;

	private static Context mContext;
	private static SharedPreferences mAppPrefs;

	private static final String ON_BOOT_PROFILE = "onBootProfile";
	private static final String RESTART_ON_BOOT = "restartvpnonboot" + "_FIXME"; // FIXME

	private static OcsVpnProfile mLastConnectedVpn=null;

	public static void init(Context context) {
		mContext = context;
		mAppPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		mProfiles = new HashMap<String, OcsVpnProfile>();

		File prefsdir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
	    if (prefsdir.exists() && prefsdir.isDirectory()) {
	    	for (String s : prefsdir.list()) {
	    		if (s.startsWith(PROFILE_PFX)) {
	    			SharedPreferences p = context.getSharedPreferences(s.replaceFirst(".xml", ""), Context.MODE_PRIVATE);
	    			OcsVpnProfile entry = new OcsVpnProfile(p);
	    			if (!entry.isValid()) {
	    				Log.w(TAG, "removing bogus profile '" + s + "'");
	    				File f = new File(s);
	    				f.delete();
	    			} else {
	    				mProfiles.put(entry.getUUIDString(), entry);
	    			}
	    		}
	    	}
	    }
	}

	public synchronized static Collection<OcsVpnProfile> getProfiles() {
		init(mContext);
		return mProfiles.values();
	}

	public synchronized static OcsVpnProfile get(String key) {
		return key == null ? null : mProfiles.get(key);
	}

	public static String getPrefsName(String uuid) {
		return PROFILE_PFX + uuid;
	}

	private static String capitalize(String in) {
		if (in.length() <= 4) {
			// These are almost always abbreviations
			return in.toUpperCase(Locale.getDefault());
		} else {
			// Longer names -> capitalize first letter only
			return Character.toUpperCase(in.charAt(0)) + in.substring(1);
		}
	}

	private static String makeProfName(String s, int index) {
		String orig = s;
		String suffix;

		if (index > 0) {
			suffix = " (" + index + ")";
		} else {
			suffix = "";
		}

		// leave IP addresses alone
		if ((s.matches("[0-9.]+") && s.matches(".*\\..*")) ||
			(s.matches("[0-9a-fA-F:]+") && s.matches(".*:.*"))) {
			return s + suffix;
		}

		// try to parse the hostname out of an URL
		if (s.matches(".*/.*")) {
			if (!s.matches("https://.*")) {
				s = "https://" + s;
			}

			s = Uri.parse(s).getHost();
			if (s == null || s.trim().equals("")) {
				// failed
				return orig + suffix;
			}
		}

		String ss[] = s.split("\\.");
		if (ss.length < 2) {
			// unqualified hostname (or junk)
			return capitalize(s) + suffix;
		}

		// Try to find the first private part of the FQDN.
		// This should probably use something like the Apache Public Suffix List, but it's not
		// worth the trouble right now.
		int i = ss.length - 1;
		if (ss[i].length() <= 2 && i > 1) {
			// if the TLD looks like a country code, check for a public SLD like .co
			String sld = ss[i - 1];
			if (sld.length() <= 2 || sld.equals("com")) {
				i--;
			}
		}

		s = ss[i - 1];
		if (s.length() < 2) {
			return orig + suffix;
		} else {
			return capitalize(s) + suffix;
		}
	}

	public synchronized static OcsVpnProfile create(String hostname) {
		String profName;

		// generate a non-conflicting name if necessary
		for (int i = 0; ; i++) {
			profName = makeProfName(hostname, i);
			if (getProfileByName(profName) == null) {
				break;
			}
		}

		String uuid = UUID.randomUUID().toString();
		SharedPreferences p = mContext.getSharedPreferences(getPrefsName(uuid), Context.MODE_PRIVATE);
		p.edit().putString("server_address", hostname).commit();

		OcsVpnProfile profile = new OcsVpnProfile(p, uuid, profName);
		mProfiles.put(uuid, profile);
		return profile;
	}

	public synchronized static OcsVpnProfile getProfileByName(String name) {
		String lower = name.toLowerCase(Locale.getDefault());
		for (OcsVpnProfile vpnp : mProfiles.values()) {
			String vname = vpnp.getName().toLowerCase(Locale.getDefault());
			if(vname.equals(lower)) {
				return vpnp;
			}
		}
		return null;
	}

	private static String getCertFilename(OcsVpnProfile profile, String key) {
		return 	"cert." + profile.getUUIDString() + "." + key;
	}

	public static String getCertPath() {
		return mContext.getFilesDir().getPath() + File.separator;
	}

	public synchronized static void deleteFilePref(OcsVpnProfile profile, String key) {
		String oldVal = profile.mPrefs.getString(key, null);
		if (getCertFilename(profile, key).equals(oldVal)) {
			File f = new File(getCertPath() + oldVal);
			if (!f.delete()) {
				Log.w(TAG, "error deleting " + oldVal);
			}
		}
	}

	public synchronized static String storeFilePref(OcsVpnProfile profile, String key, String fromPath) {
		String filename = getCertFilename(profile, key);
		String toPath = getCertPath() + filename;

		try {
			FileInputStream in = new FileInputStream(fromPath);
			File outFile = new File(toPath);
			FileOutputStream out = new FileOutputStream(outFile);
			byte buffer[] = new byte[65536];

			int len = in.read(buffer);
			out.write(buffer, 0, len);

			in.close();
			out.close();
			outFile.setExecutable(true);

			return filename;
		} catch (Exception e) {
			Log.e(TAG, "error copying " + fromPath + " -> " + toPath, e);

			try {
				new File(toPath).delete();
			} catch (Exception ee) {
			}

			return null;
		}
	}

	public synchronized static boolean delete(String uuid) {
		OcsVpnProfile profile = get(uuid);
		if (profile == null) {
			Log.w(TAG, "error looking up profile " + uuid);
			return false;
		}

		for (String key : fileSelectKeys) {
			deleteFilePref(profile, key);
		}

		mProfiles.remove(uuid);

		File f = new File(mContext.getApplicationInfo().dataDir + File.separator +
				"shared_prefs" + File.separator + PROFILE_PFX + uuid + ".xml");

		if (f.delete()) {
			Log.i(TAG, "deleted profile " + uuid);
			return true;
		} else {
			Log.w(TAG, "error deleting profile " + uuid);
			return false;
		}
	}

	public synchronized static void setConnectedVpnProfileDisconnected() {
		mLastConnectedVpn = null;
		mAppPrefs.edit()
			.remove(ON_BOOT_PROFILE)
			.commit();
	}

	public synchronized static void setConnectedVpnProfile(OcsVpnProfile connectedProfile) {
		mLastConnectedVpn = connectedProfile;
		mAppPrefs.edit()
			.putString(ON_BOOT_PROFILE, connectedProfile.getUUIDString())
			.commit();
	}

	public synchronized static OcsVpnProfile getOnBootProfile() {
		if (!mAppPrefs.getBoolean(RESTART_ON_BOOT, false)) {
			return null;
		}
		return get(mAppPrefs.getString(ON_BOOT_PROFILE, null));
	}

	public static OcsVpnProfile getLastConnectedVpn() {
		return mLastConnectedVpn;
	}

}
