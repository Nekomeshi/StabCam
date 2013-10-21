package com.nekomeshi312.stabcam;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;


import com.nekomeshi312.cameraandparameters.CameraAndParameters;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ZoomButton;

public class StabCameraView extends FrameLayout
							implements Camera.PreviewCallback,
										SurfaceHolder.Callback,
										CameraSurfaceView.PreviewInterface{
	public interface CameraViewInterface{
		public void initImageProcess(int width, int height);
		public void stopImageProcess();
		public boolean doImageProcess(byte[]img, Bitmap bmp);
		public boolean showCameraOpenProgressDialog(boolean toShow);
	}
	/**
	 * 画像処理スレッドとUIスレッドでの表示とで同じ画像を見ないようにするためのDoubleBuffer
	 * @author masaki
	 *
	 */
	private class ProcessedBmpDblBuffer{
		private static final int BUF_NUM = 2;
	    private Bitmap [] mProcessedBmp = new Bitmap[BUF_NUM];
	    private ReentrantReadWriteLock [] mLock = new ReentrantReadWriteLock[BUF_NUM];
	    private int mNewBmpNumber = -1;
	    ProcessedBmpDblBuffer(int w, int h){
	    	for(int i = 0;i < BUF_NUM;i++){
				mProcessedBmp[i] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
				mLock[i] = new ReentrantReadWriteLock();
	    	}
	    }
	    /**
	     * 読み込めるバッファを選択し、ReadLockをかける
	     * @return Lockをかけたバッファの番号
	     */
	    public int readLock(){
	    	if(mNewBmpNumber < 0) return -1;
	    	for(int i = 0;i < BUF_NUM;i++){
	    		final int no = (i + mNewBmpNumber ) % BUF_NUM;
	    		if(mLock[no].isWriteLocked())continue;//WriteLockされている == 画像処理中の場合は別のBufferを見る
	    		mLock[no].readLock().lock();
	    		return no;
	    	}
	    	return -1;
	    }
	    /**
	     * Read Lockを解除する
	     * @param bufNo　解除するバッファの番号
	     */
	    public void readUnlock(int bufNo){
	    	mLock[bufNo].readLock().unlock();
	    }
	    /**
	     * 書き込めるバッファを選択し、WriteLockをかける
	     * @return Lockかけたバッファの番号
	     */
	    public int writeLock(){
	    	for(int i = 0;i < BUF_NUM;i++){
	    		final int no = (i + 1 + mNewBmpNumber) % BUF_NUM;
	    		if(mLock[no].getReadLockCount()>0) continue;//ReadLockされている == 表示中の場合は別のBufferを見る
	    		mLock[no].writeLock().lock();
	    		mNewBmpNumber = no;
	    		return no;
	    	}
	    	return -1;
	    }
	    /**
	     * Write Lockを解除する
	     * @param bufNo　解除する バッファの番号
	     */
	    public void writeUnlock(int bufNo){
	    	mLock[bufNo].writeLock().unlock();
	    }
	}
	private static final String LOG_TAG = "StabCameraView";
	private CameraAndParameters mCameraSetting = null;
	private CameraViewInterface mCamViewInterface = null;
	private Context mContext = null;
	private int mWidth = 0;
	private int mHeight = 0;
	private static final int BUFFER_NUM = 2;
	private byte[][] mBuffer = null;
	private CameraSurfaceView mCameraSurfaceView;
	private SurfaceHolder mSurfaceHolder;
	private boolean mAspectFitToScreen = true;
	private boolean mSizeFitToScreen = true;
	private StartPreviewAsyncTask mStartPreviewTask = null;
	private boolean mIsPreviewStarted = false;
	private boolean mAFRunning = false;
	
	private ProcessedBmpDblBuffer mProcessedBmp = null;
	
	public StabCameraView(Context context) {
		this(context, null);
		// TODO Auto-generated constructor stub
	}
	public StabCameraView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
		// TODO Auto-generated constructor stub
	}
	public StabCameraView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
		
		//attrs.xmlに定義したスタイルのインスタンスを作成
		TypedArray a = context.obtainStyledAttributes(attrs,
													R.styleable.StabCameraView);
		//previewのアスペクトを、画面解像度に合わせるか(=true)、撮影解像度に合わせるか(=false)
		mAspectFitToScreen = a.getBoolean(R.styleable.StabCameraView_aspect_fit_to_screen, true);
		mSizeFitToScreen = a.getBoolean(R.styleable.StabCameraView_size_fit_to_screen, true);
		a.recycle();
		
		//カメラSurfaceView追加
		mContext = context;
		mCameraSurfaceView = new CameraSurfaceView(mContext);
		mCameraSurfaceView.setPreviewInterface(this);
		FrameLayout.LayoutParams prm = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		prm.gravity = Gravity.CENTER;
		mCameraSurfaceView.setLayoutParams(prm);
		addView(mCameraSurfaceView);
		mCameraSurfaceView.setOnTouchListener(new View.OnTouchListener() {//画面touchでAFスタート
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// TODO Auto-generated method stub
				Camera camera = mCameraSetting.getCamera();
				if(camera == null) return true;
				if(mAFRunning) return true;
				mAFRunning = true;
				camera.autoFocus(new Camera.AutoFocusCallback() {
					@Override
					public void onAutoFocus(boolean success, Camera camera) {
						// TODO Auto-generated method stub
						mAFRunning = false;
					}
				});
				return true;
			}
		});

		//Zoom UI追加
		LayoutInflater inflater = (LayoutInflater)mContext.getSystemService (Context.LAYOUT_INFLATER_SERVICE);
		View zoomLayout = inflater.inflate(R.layout.digital_zoom_ui, null);
		prm = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		zoomLayout.setLayoutParams(prm);
		addView(zoomLayout);
        
		mSurfaceHolder = mCameraSurfaceView.getHolder();
		
		mSurfaceHolder.addCallback(this);
		if(Build.VERSION.SDK_INT < 11){
			mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
	}
	
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		// TODO Auto-generated method stub
		super.onLayout(changed, l, t, r, b);
		if(changed){
			mWidth = r - l;
			mHeight = b - t;
			if(StabCamDebug.Debug){
				Log.d(LOG_TAG, "onLayout pos = " + l + ":" + t + ":" + r  + ":" + b);
			}
			startPreview();
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub
		if(StabCamDebug.Debug)Log.d(LOG_TAG, "surface changed = " + width + height);
	}
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub

	}
	private Integer mBufferNo = 0;
	private void addCallbackBuffer(boolean useNextBuffer){
		if(useNextBuffer) mBufferNo = (mBufferNo + 1) % BUFFER_NUM;
		
		mCameraSetting.getCamera().addCallbackBuffer(mBuffer[mBufferNo]);
	}
	private Thread mImgProcThread = null;
	long mTimeToMeasure = 0;

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		// TODO Auto-generated method stub

		if(mCameraSetting == null) return;
		if(mCameraSetting.getCamera() == null) return;
		if(!mCameraSetting.isCameraOpen()) return;
		if(mProcessedBmp == null) return;
		if(mImgProcThread != null && mImgProcThread.isAlive()){
			try {
				mImgProcThread.join(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			finally{
				if(mImgProcThread.isAlive()){
					addCallbackBuffer(false);
					Log.w(LOG_TAG, "Image Proc Thread interupted.");
					return;
				}
			}
		}
		
		final byte[] d = data;
		mImgProcThread = new Thread(new Runnable(){

			@Override
			public void run() {
				// TODO Auto-generated method stub
				if(mCamViewInterface != null){
					long t0 = 0;
					if(StabCamDebug.Debug){
						t0 = System.currentTimeMillis();
						Log.d(LOG_TAG, "onPrviewFrame Thread0 = " + (t0 - mTimeToMeasure));
						mTimeToMeasure = t0;
					}

					final int imgNo = mProcessedBmp.writeLock();
					if(imgNo < 0){
						Log.w(LOG_TAG, "Can't write lock processed bmp");
						return;
					}
					mCamViewInterface.doImageProcess(d, mProcessedBmp.mProcessedBmp[imgNo]);
					mCameraSurfaceView.postInvalidate();
					mProcessedBmp.writeUnlock(imgNo);
			
					if(StabCamDebug.Debug){
						t0 = System.currentTimeMillis();
						Log.d(LOG_TAG, "onPrviewFrame Thread1 = " + (t0 - mTimeToMeasure));
						mTimeToMeasure = t0;
					}
				}
			}
		});
		mImgProcThread.start();
		addCallbackBuffer(true);
	}

	public boolean openCamera(){
		if(mCameraSetting == null){
			mCameraSetting = CameraAndParameters.newInstance(mContext);
		}
        return mCameraSetting.openCamera(CameraAndParameters.CAMERA_FACING_BACK) != null;
	}
	public boolean closeCamera(){
		if(mCameraSetting.isCameraOpen()){
			mCameraSetting.releaseCamera();
		}
		return true;
	}
	public boolean isCameraOpen(){
		return mCameraSetting.isCameraOpen();
	}
	
	
	public void setCameraViewInterface(CameraViewInterface i){
		mCamViewInterface = i;
	}
	
	
	private class StartPreviewAsyncTask extends AsyncTask<Integer, Integer, Boolean>{
		private boolean setLayoutFinished = false;
		private void setupViews(final int camWidth, 
								final int camHeight, 
								final int zoomMin, 
								final int zoomMax, 
								final int zoomCur){
			//camera のSurfaceViewの位置を設定
			if(mSizeFitToScreen){
				if((float)mWidth/(float)mHeight > (float)camWidth/(float)camHeight){//スクリーンのほうがカメラのアスペクトより横長
					final int l = (int)(((float)mWidth - (float)camWidth*(float)mHeight/(float)camHeight)/2.0 + 0.5);
					FrameLayout.LayoutParams prm = new FrameLayout.LayoutParams(mWidth - 2*l, mHeight, Gravity.LEFT);
					prm.leftMargin = l;
					mCameraSurfaceView.setLayoutParams(prm);
				}
				else{
					final int t = (int)(((float)mHeight - (float)camHeight*(float)mWidth/(float)camWidth)/2.0 + 0.5);
					FrameLayout.LayoutParams prm = new FrameLayout.LayoutParams(mWidth, mHeight - 2*t, Gravity.TOP);
					prm.topMargin = t;
					mCameraSurfaceView.setLayoutParams(prm);
				}
			}
			else{
		        final int l = (int) ((mWidth - camWidth)/2.0 + 0.5);
		        final int t = (int) ((mHeight - camHeight)/2.0 + 0.5);
				FrameLayout.LayoutParams prm = new FrameLayout.LayoutParams(mWidth - 2*l, mHeight - 2*t, Gravity.TOP|Gravity.LEFT);
				prm.topMargin = t;
				prm.leftMargin = l;
				mCameraSurfaceView.setLayoutParams(prm);
			}
			//Zoom UIの位置&Callback&SeekBarの値を設定
			LinearLayout l = (LinearLayout) findViewById(R.id.zoom_base_layout);
			if(zoomMin == zoomMax){//zoomがない端末の場合は表示しない
				l.setVisibility(View.GONE);
			}
			else{
				l.setVisibility(View.VISIBLE);
				SeekBar zoomSeek = (SeekBar) findViewById(R.id.zoom_seek);
				zoomSeek.setMax(zoomMax - zoomMin);
				zoomSeek.setProgress(zoomCur);
				zoomSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
					
					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
						// TODO Auto-generated method stub
						
					}
					
					@Override
					public void onProgressChanged(SeekBar seekBar, int progress,
							boolean fromUser) {
						// TODO Auto-generated method stub
						mCameraSetting.mZoom.setValueToPref(progress + zoomMin, true);
					}
				});
				ZoomButton zoomMinus = (ZoomButton) findViewById(R.id.zoom_button_minus);
				zoomMinus.setOnClickListener(new View.OnClickListener() {
					
					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
						int zoomVal = mCameraSetting.mZoom.getValueFromPref() - 1;
						zoomVal = zoomVal < zoomMin ? zoomMin:zoomVal;
						mCameraSetting.mZoom.setValueToPref(zoomVal, true);
					}
				});
				
				ZoomButton zoomPlus = (ZoomButton)findViewById(R.id.zoom_button_plus);
				zoomPlus.setOnClickListener(new View.OnClickListener() {
					
					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
						int zoomVal = mCameraSetting.mZoom.getValueFromPref() + 1;
						zoomVal = zoomVal > zoomMax ? zoomMax:zoomVal;
						mCameraSetting.mZoom.setValueToPref(zoomVal, true);
					}
				});
			}
		}
		/* (non-Javadoc)
		 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
		 */
		@Override
		protected void onProgressUpdate(Integer... values) {
			// TODO Auto-generated method stub
			super.onProgressUpdate(values);
			setupViews(values[0], values[1], values[2], values[3], values[4]);
	        setLayoutFinished = true;
		}

		/* (non-Javadoc)
		 * @see android.os.AsyncTask#onCancelled()
		 */
		@Override
		protected void onCancelled() {
			// TODO Auto-generated method stub
			super.onCancelled();
			if(mCamViewInterface != null){
				mCamViewInterface.showCameraOpenProgressDialog(false);
			}
		}

		/* (non-Javadoc)
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(Boolean result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			if(mCamViewInterface != null){
				mCamViewInterface.showCameraOpenProgressDialog(false);
			}
		}

		/* (non-Javadoc)
		 * @see android.os.AsyncTask#onPreExecute()
		 */
		@Override
		protected void onPreExecute() {
			// TODO Auto-generated method stub
			super.onPreExecute();
			if(mCamViewInterface != null){
				mCamViewInterface.showCameraOpenProgressDialog(true);
			}
		}

		@Override
		protected Boolean doInBackground(Integer... params) {
			// TODO Auto-generated method stub
			if(!mCameraSetting.isCameraOpen()){
				Log.w(LOG_TAG, "Camera does not open");
				return false;
			}

			if(StabCamDebug.Debug)Log.d(LOG_TAG, "wh = " + mWidth + ":" + mHeight);
			try {
				stopPreview();
				setLayoutFinished = false;
				Size prevSize;
				if(CameraSettingActivity.isPreviewSizeFitToScreen(mContext)){//キャプチャ解像度を画面解像度にあわせる場合
					prevSize = calcPreviewSize(mWidth, mHeight, mAspectFitToScreen);
					mCameraSetting.mPreviewSize.setValueToPref(prevSize, true);
				}
				else{//画面解像度を手動で設定する場合
					prevSize = mCameraSetting.mPreviewSize.getValueFromPref();
					mCameraSetting.mPreviewSize.setValueToCam();//Preferenceの値をカメラにセット
				}
				//PicSizeとPrevSizeとでaspectが違うとちゃんと動かない端末があるので、とりあえずPicSizeをセットしておく
				List<Size> picSizes = mCameraSetting.mPictureSize.getSupportedList();
				if(picSizes != null){
					float prevAspect = (float)prevSize.width/(float)prevSize.height;
					float aspectDiv = Float.MAX_VALUE;
					Size picSize = null;
					for(Size p:picSizes){
						float ad = Math.abs((float)p.width/(float)p.height) - prevAspect;
						ad = Math.abs(ad);
						if(ad < aspectDiv){
							picSize = p;
							aspectDiv = ad;
						}
					}
					if(picSize != null){
						mCameraSetting.mPictureSize.setValueToPref(picSize, true);
						if(StabCamDebug.Debug) Log.d(LOG_TAG, "pic size = " + picSize.width + ":" + picSize.height);
					}
				}
				int zoomMin = 0;
				int zoomMax = 0;
				int zoomCur = 0;
				
				if(mCameraSetting.mZoom.isSupported()){
					zoomMin = mCameraSetting.mZoom.getMinValue();
					zoomMax = mCameraSetting.mZoom.getMaxValue();
					zoomCur = mCameraSetting.mZoom.getValueFromPref();
					mCameraSetting.mZoom.setValueToCam();
				}
				if(mCameraSetting.mExposurecompensation.isSupported()) mCameraSetting.mExposurecompensation.setValueToCam();
				if(mCameraSetting.mWhiteBalance.isSupported()) mCameraSetting.mWhiteBalance.setValueToCam();

				publishProgress(prevSize.width, prevSize.height, zoomMin, zoomMax, zoomCur);
				
				//プレビューフレームレートを最大に設定
				if(!mCameraSetting.mPreviewFpsRange.isSupported()){
					List<Integer> fpsList = mCameraSetting.mPreviewFrameRate.getSupportedList();
					int maxV = 0;
					int maxPos = 0;
					for(int i = 0;i < fpsList.size();i++){
						if(fpsList.get(i) > maxV){
							maxPos = i;
							maxV = fpsList.get(i);
						}
					}
					mCameraSetting.mPreviewFrameRate.setValueToPref(maxPos, true);
				}
				else{
					List<int[]> fpsList = mCameraSetting.mPreviewFpsRange.getSupportedList();
					int maxV = 0;
					int[] fps = {0, 0};
					for(int i = 0;i < fpsList.size();i++){
						if(fpsList.get(i)[0] > maxV){
							fps = fpsList.get(i);
							maxV = fpsList.get(i)[0];
						}
					}
					mCameraSetting.mPreviewFpsRange.setValueToPref(fps, true);
				}
				
				if(mCameraSetting.mFocusMode.isSupported()){//Continuous AFに対応している場合は設定
					List<String> afModes = mCameraSetting.mFocusMode.getSupportedList();
					for(String mode:afModes){
						if(Build.VERSION.SDK_INT >= 14 && mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)){
							mCameraSetting.mFocusMode.setValueToPref(mode, true);
							Log.i(LOG_TAG, "Focus mode = " + mode);
							break;
						}
						if(Build.VERSION.SDK_INT >= 9 && mode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)){
							mCameraSetting.mFocusMode.setValueToPref(mode, true);
							Log.i(LOG_TAG, "Focus mode = " + mode);
							break;
						}
					}
				}
				Thread.sleep(100);//現在進行中のキャプチャがすべて掃けるのを待つ
				setPreviewCallback();
				
				//CameraSurfaceViewのlayoutが完了するのを待つ
				while(!setLayoutFinished){
					Thread.sleep(100);
				}
				
				if(mCamViewInterface != null){
					mCamViewInterface.initImageProcess(prevSize.width, prevSize.height);
				}
				mCameraSetting.getCamera().setPreviewDisplay(mSurfaceHolder);
				mCameraSetting.getCamera().startPreview();
				mIsPreviewStarted = true;
				return true;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return false;
		}
	}
	
	public boolean startPreview() {
		// TODO Auto-generated method stub
		if(getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE){
			Log.w(LOG_TAG, "startPreview Orientaiton = Portrait");
			return false;
		}

		if(mWidth <= 0 || mHeight <= 0){
			if(StabCamDebug.Debug) Log.d(LOG_TAG, "width = " + mWidth + "height = " + mHeight);
			return false;
		}
		if(!mCameraSetting.isCameraOpen()){
			Log.w(LOG_TAG, "Camera not open");
			return false;
		}
		if(mIsPreviewStarted){
			Log.w(LOG_TAG, "Preview is alrealy started");
			return true;
		}
		if(mStartPreviewTask != null && mStartPreviewTask.getStatus() == AsyncTask.Status.RUNNING){
			Log.w(LOG_TAG, "Start Preview task is running.");
			return true;
		}
		StartPreviewAsyncTask prev = new StartPreviewAsyncTask();
		prev.execute(mWidth, mHeight);
		return true;
	}

	public void stopPreview() {
		// TODO Auto-generated method stub
		Log.i(LOG_TAG, "stopPreview");
		if(mCameraSetting == null) return;
		if(!mIsPreviewStarted){
			Log.w(LOG_TAG, "Preview Not started");
			return;
		}
		try{
			if(mCameraSetting.getCamera() != null){
				final Camera camera = mCameraSetting.getCamera();
				camera.stopPreview();
				while(mImgProcThread != null && mImgProcThread.isAlive()){
					Log.d(LOG_TAG, "Waiting image process thread finish");
					Thread.sleep(100);
				}
            	camera.setPreviewCallbackWithBuffer(null);
            	if(mBuffer != null){
                	for(int i = 0;i < BUFFER_NUM;i++) mBuffer[i] = null;
                	mBuffer = null;
            	}
				mIsPreviewStarted = false;
				if(mCamViewInterface != null){
					mCamViewInterface.stopImageProcess();
				}
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}

	private void setPreviewCallback(){
		if(!mCameraSetting.isCameraOpen()) return;

		final Camera camera = mCameraSetting.getCamera();
		//カメラの色深度を取得 bit/pixel
		PixelFormat pixelinfo = new PixelFormat();
		int pixelformat = camera.getParameters().getPreviewFormat();
		PixelFormat.getPixelFormatInfo(pixelformat, pixelinfo);
		//カメラのプレビュー解像度を取得
        Camera.Parameters parameters = camera.getParameters();
        Size sz = parameters.getPreviewSize();
        //プレビュー画像のサイズを計算し、バッファを確保
		int bufSize = sz.width * sz.height * pixelinfo.bitsPerPixel / 8;
		mBuffer = new byte[BUFFER_NUM][];
		for(int i = 0;i < BUFFER_NUM;i++){
			mBuffer[i] = new byte[bufSize];
		}
		//画像処理用と表示用とでかち合わないようにDouble Bufferにする
		mProcessedBmp = new ProcessedBmpDblBuffer(sz.width, sz.height);

		addCallbackBuffer(true);
//		camera.addCallbackBuffer(mBuffer);
		camera.setPreviewCallbackWithBuffer(this);
	}
	
	private Size calcPreviewSize(int baseWidth, int baseHeight, boolean fitToSurface){

		double picAspect;
		if(mAspectFitToScreen){//画面のアスペクトに合わせる場合
			picAspect = (double)baseWidth/(double)baseHeight;
		}
		else{//撮影解像度にアスペクトを合わせる場合
			//撮影解像度取得
			Size picSize = mCameraSetting.mPictureSize.getValueFromPref();
			if(picSize == null) return null;
			picAspect = (double)picSize.width/(double)picSize.height;
			if(StabCamDebug.Debug){
				Log.d(LOG_TAG, "picSize = " + picSize.width + ":" + picSize.height);
			}
		}
		
		List<Size> size = mCameraSetting.mPreviewSize.getSupportedList();
		if(StabCamDebug.Debug){
			Log.d(LOG_TAG, "size_num = " + size.size());
			Log.d(LOG_TAG, "measurespecSize = " + baseWidth + ":" + baseHeight);
		}
		final double ASPECT_TOLERANCE = 0.1;
        double minDiff = Double.MAX_VALUE;
        double minAspect = Double.MAX_VALUE;

		Size camS = null;
		for(int i = 0;i < 2;i++){
			for(Size s:size){//preview sizeのリストから、最初はviewに近い解像度で、撮影解像度に最も近いアスペクトとなるものを探す。それがなければでview横幅以下で、view高さ以下で最大となるものを探す
				if(StabCamDebug.Debug) Log.d(LOG_TAG, "prev size list = " + s.width + "/" + s.height);
				if(i == 0){
					if(Math.abs(s.width - baseWidth) > baseWidth/10.0) continue;
					if(Math.abs(s.height - baseHeight) > baseHeight/10.0)continue;
				}
				else{
					if(s.width > baseWidth) continue;
					if(s.height > baseHeight)continue;
				}
				final double aspect = (double)s.width/(double)s.height;
				final double diffW = Math.abs(s.width - baseWidth);
				final double diffH = Math.abs(s.height - baseHeight);
				if(minAspect < ASPECT_TOLERANCE ){
					if(Math.abs(aspect - picAspect) >= ASPECT_TOLERANCE) continue;
		            if (Math.min(diffW, diffH) < minDiff) {
		            	camS = s;
		            	minDiff = Math.min(diffW, diffH);
		            }
				}
				else if(Math.abs(aspect - picAspect) < minAspect) {
	            	minAspect = Math.abs(aspect - picAspect);
	            	camS = s;
	            	minDiff = Math.min(diffW, diffH);
	            }
			}
			if(camS != null){
				Log.i(LOG_TAG, "preview size = " + camS.width + ":" + camS.height);
				break;
			}
		}
		return camS;
	}
	
	private int mReadLockImgNumber = -1;
	@Override
	public Bitmap getProcessedBitmap() {
		// TODO Auto-generated method stub
		if(mProcessedBmp == null) return null;
		if(mReadLockImgNumber < 0){
			Log.w(LOG_TAG, "Bitmap no locked.");
			return null;
		}
		return mProcessedBmp.mProcessedBmp[mReadLockImgNumber];
	}
	@Override
	public boolean lockReadBitmapProcess(boolean lock) {
		// TODO Auto-generated method stub
		if(mProcessedBmp == null) return false;
		if(lock){
			mReadLockImgNumber = mProcessedBmp.readLock();
			if(mReadLockImgNumber < 0) return false;//lockできなかった
		}
		else{
			if(mReadLockImgNumber < 0) return false;//lockされていない
			mProcessedBmp.readUnlock(mReadLockImgNumber);
		}
		return true;
	}
}
