package com.nekomeshi312.stabcam;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

public class StabCameraViewFragment extends Fragment
									implements StabCameraView.CameraViewInterface{
	private static final String LOG_TAG = "CameraViewFragment";
	private static final int ACTIVIY_REQUEST_CAMERA_SETTING_PREF = 0;
	
	private StabCameraView mCameraView = null;
	private boolean mUseVR = true;

	private Mat mProcessedImg = null;
    native private void initStabilize(int width, int height);
    native private void stopStabilize();
    native private void getStabilizedImage(byte [] camImg, long matStabizliedImage, boolean usrVR);

	static {
		try{
	        System.loadLibrary("stabilized_camera");
		} catch (UnsatisfiedLinkError e) {
			e.printStackTrace();
			throw e;
		}
    } 

	public static StabCameraViewFragment newInstance(){
		StabCameraViewFragment frag = new StabCameraViewFragment();
		return frag;
	}
	
	/* (non-Javadoc)
	 * @see com.actionbarsherlock.app.SherlockFragment#onAttach(android.app.Activity)
	 */
	@Override
	public void onAttach(Activity activity) {
		// TODO Auto-generated method stub
		super.onAttach(activity);
		this.setHasOptionsMenu(true);
	}
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		// TODO Auto-generated method stub
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.fragment_camera_view, container, false);
        
        mCameraView = (StabCameraView) root.findViewById(R.id.camera_view);
        mCameraView.setCameraViewInterface(this);
        return root;
	}
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onActivityCreated(android.os.Bundle)
	 */
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onActivityCreated(savedInstanceState);
        if(!mCameraView.openCamera()){
        	Toast.makeText(getActivity(), R.string.error_start_camera, Toast.LENGTH_SHORT).show();
        }
	}
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onDestroyView()
	 */
	@Override
	public void onDestroyView() {
		// TODO Auto-generated method stub
		super.onDestroyView();
		mCameraView.closeCamera();
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onResume()
	 */
	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		if(getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE){
			Log.w(LOG_TAG, "onResume Orientaiton = Portrait");
			return;
		}
		mCameraView.startPreview();
	}

	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onPause()
	 */
	@Override
	public void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		if(getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE){
			Log.w(LOG_TAG, "onPause Orientaiton = Portrait");
			return;
		}
		mCameraView.stopPreview();
	}
	
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateOptionsMenu(android.view.Menu, android.view.MenuInflater)
	 */
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// TODO Auto-generated method stub
	   	inflater.inflate(R.menu.fragment_stab_camera_view, menu);
	   	MenuItem item = menu.findItem(R.id.menu_switch_vr);
	   	View v = MenuItemCompat.getActionView(item);
    	org.jraf.android.backport.switchwidget.Switch s = (org.jraf.android.backport.switchwidget.Switch) v.findViewById(R.id.switch_vr);
    	s.setOnCheckedChangeListener(new OnCheckedChangeListener(){

			@Override
			public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
				// TODO Auto-generated method stub
				mUseVR = arg1;
			}
    	});
    	
    	super.onCreateOptionsMenu(menu, inflater);
	}
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		if(StabCamDebug.Debug) Log.i(LOG_TAG, item.toString());
		switch(item.getItemId()){
			case R.id.menu_settings:
				Intent i= new Intent(getActivity(), 
							CameraSettingActivity.class);
				startActivityForResult(i, ACTIVIY_REQUEST_CAMERA_SETTING_PREF);
				return true;
			default:
				break;
		}
		return super.onOptionsItemSelected(item);
	}
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onPrepareOptionsMenu(android.view.Menu)
	 */
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		super.onPrepareOptionsMenu(menu);
	   	MenuItem menuItem = menu.findItem(R.id.menu_switch_vr);
	   	View v = MenuItemCompat.getActionView(menuItem);
		org.jraf.android.backport.switchwidget.Switch s = 
					(org.jraf.android.backport.switchwidget.Switch) v.findViewById(R.id.switch_vr);
		s.setChecked(mUseVR);
	}
	
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	@Override
	public void initImageProcess(int width, int height) {
		// TODO Auto-generated method stub
		initStabilize(width, height);
		mProcessedImg = new Mat(height, width, CvType.CV_8UC4);
	}
	@Override
	public void stopImageProcess() {
		// TODO Auto-generated method stub
		stopStabilize();
		if(mProcessedImg != null){
			mProcessedImg.release();
		}
	}
	
	@Override
	public boolean doImageProcess(byte[] img, Bitmap processedBmp) {
		// TODO Auto-generated method stub
	    getStabilizedImage(img, mProcessedImg.getNativeObjAddr(), mUseVR);
		Utils.matToBitmap(mProcessedImg, processedBmp);
		return true;
	}
	private ProgressDialog mWaitOpenDialog;

	@Override
	public boolean showCameraOpenProgressDialog(boolean toShow) {
		// TODO Auto-generated method stub
		if(toShow){
			Context context = getActivity();
			mWaitOpenDialog = new ProgressDialog(context);
			String msg = context.getString(R.string.starting_camera);
			mWaitOpenDialog.setMessage(msg);
		    // 円スタイル（くるくる回るタイプ）に設定します
			mWaitOpenDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			mWaitOpenDialog.setCancelable(false);
		    // プログレスダイアログを表示
			mWaitOpenDialog.show();
		}
		else{
			mWaitOpenDialog.dismiss();
		}
		
		return false;
	}

}
