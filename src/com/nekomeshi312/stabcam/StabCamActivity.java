package com.nekomeshi312.stabcam;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

public class StabCamActivity extends ActionBarActivity  {

	private static final String LOG_TAG = "StabCamActivity";
	
	private static final String STAB_CAMERA_FRAG_TAG = "STAB_CAMERA_FRAG_TAG";
	
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                	Log.i(LOG_TAG, "OpenCV loaded successfully");
            		transitToStabilizedCameraFragment();
            		break;
                default:
					super.onManagerConnected(status);
                	break;
            }
        }
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
    	//action bar をオーバレイ表示させる。フルsクリーン表示してstatus barを非表示
    	final android.view.Window wnd = getWindow();
        wnd.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        wnd.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

		
		setContentView(R.layout.activity_stab_cam);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        //actionbarの背景を設定（半透明のグラデーション)
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.actionbar_background));
	}

	/* (non-Javadoc)
	 * @see com.actionbarsherlock.app.SherlockFragmentActivity#onPause()
	 */
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		if(getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE){
			Log.w(LOG_TAG, "onPause Orientaiton = Portrait");
			return;
		}
		
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);        
        if(StabCamDebug.Debug)Log.d(LOG_TAG, "onPause");
	}


	/* (non-Javadoc)
	 * @see android.support.v4.app.FragmentActivity#onResume()
	 */
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		if(getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE){
			Log.w(LOG_TAG, "onResume Orientaiton = Portrait");
			removeStabilizedCameraFragment();
			return;
		}
		
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(StabCamDebug.Debug)Log.d(LOG_TAG, "onResume");
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		return super.onCreateOptionsMenu(menu);
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		return super.onOptionsItemSelected(item);
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		return super.onPrepareOptionsMenu(menu);
	}

	
	private void transitToStabilizedCameraFragment(){
		FragmentManager fm = getSupportFragmentManager();
		Fragment frag = (Fragment) fm.findFragmentByTag(STAB_CAMERA_FRAG_TAG);
		if(frag != null) return;
		frag = StabCameraViewFragment.newInstance();
		FragmentTransaction ft = fm.beginTransaction();
		ft.replace(R.id.fragment_base, frag, STAB_CAMERA_FRAG_TAG);
		// Fragmentの変化時のアニメーションを指定
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
		ft.commit();		
	}
	private void removeStabilizedCameraFragment(){
		FragmentManager fm = getSupportFragmentManager();
		Fragment frag = (Fragment) fm.findFragmentByTag(STAB_CAMERA_FRAG_TAG);
		if(frag == null) return;
		FragmentTransaction ft = fm.beginTransaction();
		ft.remove(frag);
		ft.commit();		
	}
}
