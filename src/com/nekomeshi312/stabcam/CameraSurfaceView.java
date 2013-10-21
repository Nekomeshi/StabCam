package com.nekomeshi312.stabcam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.SurfaceView;

public class CameraSurfaceView extends SurfaceView {
	private static final String LOG_TAG = "CameraSurfaceView";
	public interface PreviewInterface{
		public Bitmap getProcessedBitmap();
		public boolean lockReadBitmapProcess(boolean lock);
	}
	private PreviewInterface mPreviewInterface = null;
	private Context mContext;
	private Rect mScreenRect = null;

	public CameraSurfaceView(Context context) {
		this(context, null);
		// TODO Auto-generated constructor stub
	}
	public CameraSurfaceView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
		// TODO Auto-generated constructor stub
	}
	public CameraSurfaceView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
		mContext = context;
	}
	
	/* (non-Javadoc)
	 * @see android.view.View#onSizeChanged(int, int, int, int)
	 */
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		// TODO Auto-generated method stub
		super.onSizeChanged(w, h, oldw, oldh);
		mScreenRect = new Rect(0, 0, w, h);
	}
	
	public void setPreviewInterface(PreviewInterface i){
		mPreviewInterface = i;
	}
	
	/* (non-Javadoc)
	 * @see android.view.SurfaceView#dispatchDraw(android.graphics.Canvas)
	 */
	@Override
	protected void dispatchDraw(Canvas canvas) {
		// TODO Auto-generated method stub
		Bitmap bmp = null;
		boolean isLocked = false;
		if(mPreviewInterface != null){
			isLocked = mPreviewInterface.lockReadBitmapProcess(true);//bmpを読み込むためロックする
			if(isLocked){
				bmp = mPreviewInterface.getProcessedBitmap();
			}
		}
		if(bmp != null){
			if(mScreenRect == null || mScreenRect.width() <= 0 || mScreenRect.height() <= 0){
				canvas.drawBitmap(bmp, 0, 0, null);
			}
			else{
				final int w = bmp.getWidth();
				final int h = bmp.getHeight();
		        // 描画元の矩形イメージ
		        Rect src = new Rect(0, 0, w, h);
				canvas.drawBitmap(bmp, src, mScreenRect, null);
			}
		}
		else{
//			super.dispatchDraw(canvas);
		}
		if(isLocked) mPreviewInterface.lockReadBitmapProcess(false);//bmp ロック解除
	}
}
