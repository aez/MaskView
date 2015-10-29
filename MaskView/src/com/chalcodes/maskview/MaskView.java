package com.chalcodes.maskview;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

public class MaskView extends View {	
	/**
	 * Resource ID of the view whose background will show through the "holes"
	 * in the MaskView.
	 */
	private final int mBackgroundViewId;
	/**
	 * The background view.  May be null.
	 */
	private View mBackgroundView;
	/**
	 * Alpha mask that defines the "holes" in the MaskView.
	 */
	private final Drawable mAlphaMaskDrawable;
	/**
	 * Foreground overlay.
	 */
	private final Drawable mForegroundDrawable;
	
	/**
	 * Constructor for instances declared in XML.
	 * 
	 * @param context 
	 * @param attrs declared attributes
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@SuppressWarnings("deprecation")
	/* new getDrawable(...) was added in API 21; old version was deprecated in API 23 */
	public MaskView(Context context, AttributeSet attrs) {
		super(context, attrs);		
		final TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.MaskView,
                0, 0);
		try {
			mBackgroundViewId = a.getResourceId(R.styleable.MaskView_backgroundView, 0);
			if(mBackgroundViewId != 0) {
				final int maskId = a.getResourceId(R.styleable.MaskView_alphaMask, 0);
				if(maskId != 0) {
					if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						mAlphaMaskDrawable = getResources().getDrawable(maskId, null);
					}
					else {
						mAlphaMaskDrawable = getResources().getDrawable(maskId);
					}
				}
				else {
					mAlphaMaskDrawable = null;
				}				
			}
			else {
				mAlphaMaskDrawable = null;
			}
			final int fgId = a.getResourceId(R.styleable.MaskView_foreground, 0);
			if(fgId != 0) {
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					mForegroundDrawable = getResources().getDrawable(fgId, null);
				}
				else {
					mForegroundDrawable = getResources().getDrawable(fgId);
				}
			}
			else {
				mForegroundDrawable = null;
			}
			
		}
		finally {
			a.recycle();
		}
	}
	
	/**
	 * True if {@link #mCacheBitmap} needs to be redrawn.
	 */
	private boolean mRedraw = true;
	
	@Override
	protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		mRedraw = true;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		mBackgroundView = findPeerById(mBackgroundViewId);
	}	
	
	/**
	 * The composite bitmap.
	 */
	private Bitmap mCacheBitmap;
	
	private static final int X = 0;
	private static final int Y = 1;
	// retained to avoid frequent reallocations
	private final int[] mViewPosition = new int[2];
	private final int[] mBackgroundPosition = new int[2];
	/**
	 * The position of the background view relative to this view.  If this
	 * view is positioned below and to the right of the background view, the
	 * element values will be negative.
	 */
	private final int[] mRelativePosition = new int[2];

	@SuppressLint("DrawAllocation") // allocations only occur when mRedraw == true
	@Override
	protected void onDraw(final Canvas outputCanvas) {
		final int w = getWidth();
		final int h = getHeight();
		
		if(w <= 0 || h <= 0) {
			return;
		}
		
		final Drawable backgroundDrawable = mBackgroundView.getBackground();
		
		if(backgroundDrawable != null) {
			getLocationOnScreen(mViewPosition);
			mBackgroundView.getLocationOnScreen(mBackgroundPosition);
			final int x = mBackgroundPosition[X] - mViewPosition[X];
			final int y = mBackgroundPosition[Y] - mViewPosition[Y];
			if(mRelativePosition[X] != x || mRelativePosition[Y] != y) {
				mRelativePosition[X] = x;
				mRelativePosition[Y] = y;
				mRedraw = true;
			}
		}
		
		if(mRedraw) {
			mCacheBitmap = prepareBitmap(w, h, mCacheBitmap);
			final Canvas cacheCanvas = new Canvas(mCacheBitmap);
			
			if(backgroundDrawable != null) {
				final int bw = mBackgroundView.getWidth();
				final int bh = mBackgroundView.getHeight(); 
				final Bitmap backgroundBitmap = prepareBitmap(bw, bh, null);
				final Canvas backgroundCanvas = new Canvas(backgroundBitmap);
				backgroundDrawable.setBounds(0, 0, bw, bh);
				backgroundDrawable.draw(backgroundCanvas);
				cacheCanvas.drawBitmap(backgroundBitmap, mRelativePosition[X], mRelativePosition[Y], null);
				backgroundBitmap.recycle();

				if(mAlphaMaskDrawable != null) {
					final Bitmap maskBitmap = prepareBitmap(w, h, null);
					final Canvas maskCanvas = new Canvas(maskBitmap);
					mAlphaMaskDrawable.setBounds(0, 0, w, h);
					mAlphaMaskDrawable.draw(maskCanvas);				
					final Paint paint = new Paint();
					paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
					cacheCanvas.drawBitmap(maskBitmap, 0, 0, paint);
					maskBitmap.recycle();
				}
			}			

			if(mForegroundDrawable != null) {
				mForegroundDrawable.setBounds(0, 0, w, h);
				mForegroundDrawable.draw(cacheCanvas);
			}
			
			mRedraw = false;			
		}
		
		final Rect clip = outputCanvas.getClipBounds();
		outputCanvas.drawBitmap(mCacheBitmap, clip, clip, null);
	}

	/**
	 * Returns a transparent bitmap with the specified width and height.  If
	 * the old bitmap is reusable, it will be cleared and returned.  If not,
	 * it will be recycled and a new bitmap will be returned instead.  On API
	 * 19+, this method will try to reconfigure bitmaps that are the wrong
	 * size.
	 * 
	 * @param width the required width
	 * @param height the required height
	 * @param bitmap a bitmap to be reused, or null
	 * @return a bitmap with the correct size, possibly but not necessarily
	 * the old bitmap
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT) // for Bitmap#reconfigure(...)
	private static Bitmap prepareBitmap(final int width, final int height, Bitmap bitmap) {
		if(bitmap != null) {
			if(bitmap.isRecycled()) {
				bitmap = null;
			}
			else if(bitmap.getWidth() == width && bitmap.getHeight() == height) {
				bitmap.eraseColor(Color.TRANSPARENT);
			}
			else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				try {
					bitmap.reconfigure(width, height, Bitmap.Config.ARGB_8888);
					bitmap.eraseColor(Color.TRANSPARENT);
				}
				catch(IllegalArgumentException e) {
					bitmap.recycle(); // recommended on API 10 and lower
					bitmap = null;
				}
			}
			else {
				bitmap.recycle();
				bitmap = null;
			}
		}		
		if(bitmap == null) {
			bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		}
		return bitmap;
	}
	
	/**
	 * Finds a view within the hierarchy to which this view is attached. 
	 * 
	 * @param viewId the ID of the other view
	 * @return the other view, or null
	 */
    private View findPeerById(int viewId) {
        View root = this;
        while(root.getParent() instanceof View) {
            root = (View) root.getParent();
        }
        return root.findViewById(viewId);
    }

}
