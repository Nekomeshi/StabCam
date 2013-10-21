/*----------------------------------------------------------------------------

  WhiteBoardCorrection 

  This code is part of the following publication and was subject
  to peer review:

    "WhiteBoardCorrection" by Nekomeshi

  Copyright (c) Nekomeshi <Nekomeshi312@gmail.com>

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as
  published by the Free Software Foundation, either version 3 of the
  License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program. If not, see <http://www.gnu.org/licenses/>.

  ----------------------------------------------------------------------------*/
package com.nekomeshi312.stabcam;

import com.nekomeshi312.cameraandparameters.CameraAndParameters;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;

public class CameraSettingActivity extends PreferenceActivity {
	private static final String LOG_TAG = "CameraSettingActivity";
	private CameraAndParameters mCameraSetting;
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		if(StabCamDebug.Debug)Log.i(LOG_TAG, "onPause");
		Log.i(LOG_TAG, "onPause : camera open = " + mCameraSetting.isCameraOpen());
		super.onPause();
	}
	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		if(StabCamDebug.Debug)Log.i(LOG_TAG, "onResume");
		super.onResume();
	}
	@Override
    public void onCreate(Bundle savedInstanceState) {
		if(StabCamDebug.Debug)Log.i(LOG_TAG, "onCreate");
		super.onCreate(savedInstanceState);
		mCameraSetting = CameraAndParameters.newInstance(this);
		Log.i(LOG_TAG, "onCreate : camera open = " + mCameraSetting.isCameraOpen());

        setPreferenceScreen(createPreferenceHierarchy());
    }
	private void setNoInfoPref(PreferenceCategory parent){
        // Toggle preference
        Preference pref = new Preference(this);
        pref.setTitle(getString(R.string.setting_no_info_pref_title));
        parent.addPreference(pref);
		
	}
	private Preference mPrefPreviewSize = null;
    private PreferenceScreen createPreferenceHierarchy() {
        // Root
    	PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
    	int count = 0;
    	PreferenceCategory prefCatImaging= new PreferenceCategory(this);
    	prefCatImaging.setTitle(getString(R.string.setting_category_title_imaging));
    	root.addPreference(prefCatImaging);
    	if(null != mCameraSetting.mPreviewSize && mCameraSetting.mPreviewSize.isSupported()){

	        CheckBoxPreference checkBoxPref = new CheckBoxPreference(this);
	        checkBoxPref.setKey(getString(R.string.preview_size_fit_to_screen_key));
	        checkBoxPref.setTitle(R.string.preview_size_fit_to_screen);
	        checkBoxPref.setSummary(R.string.preview_size_fit_to_screen_summary);
	        checkBoxPref.setDefaultValue(isPreviewSizeFitToScreen(this));
	        checkBoxPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					// TODO Auto-generated method stub
					Boolean b = (Boolean)newValue;
					if(mPrefPreviewSize != null){
						mPrefPreviewSize.setEnabled(!b);
					}
					return true;
				}
			});
	        prefCatImaging.addPreference(checkBoxPref);
    		count++;

    		mCameraSetting.mPreviewSize.setPreference(prefCatImaging, this);
    		int prefNum = prefCatImaging.getPreferenceCount();
    		mPrefPreviewSize = prefCatImaging.getPreference(prefNum-1);
    		mPrefPreviewSize.setEnabled(!isPreviewSizeFitToScreen(this));
    		count++;
    	}
    
    	if(null != mCameraSetting.mZoom &&
    			true == mCameraSetting.mZoom.setPreference(prefCatImaging, this)) count++;
    	if(null != mCameraSetting.mExposurecompensation &&
    			true == mCameraSetting.mExposurecompensation.setPreference(prefCatImaging, this)) count++;
    	if(null != mCameraSetting.mWhiteBalance &&
    			true == mCameraSetting.mWhiteBalance.setPreference(prefCatImaging, this)) count++;
    	if(0 == count){
    		setNoInfoPref(prefCatImaging);
    	}
    	return root;
    }
	public static boolean isPreviewSizeFitToScreen(Context context){
		SharedPreferences sharedPreferences = 
			PreferenceManager.getDefaultSharedPreferences(context);   
		return sharedPreferences.getBoolean(context.getString(R.string.preview_size_fit_to_screen_key), true);
	}

}
