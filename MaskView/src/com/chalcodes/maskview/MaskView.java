package com.chalcodes.maskview;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

public class MaskView extends View {	
	// primary fields	
	/**
	 * Resource ID of the view that draws the background image.  Store the ID
	 * because the actual background view may not have been created at the
	 * time the mask view is constructed.
	 */
	private final int mBackgroundViewId;
	/**
	 * True if {@link #mBackgroundView} should be called upon to redraw the
	 * background every frame.  If false, it will only be redrawn when this
	 * view's size or position changes relative to the background view.
	 */
	private final boolean mDynamicBackground;
	/**
	 * Alpha mask that will be applied to the background.  This needs to be
	 * drawn at the correct size, then converted from grayscale to alpha.
	 */
	private final Drawable mAlphaMaskDrawable;
	/**
	 * Image that will be drawn in the foreground.
	 */
	private final Drawable mForegroundDrawable;
	
	/**
	 * Constructor for instances declared in XML.
	 * 
	 * @param context 
	 * @param attrs declared attributes
	 */
	public MaskView(Context context, AttributeSet attrs) {
		super(context, attrs);		
		TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.MaskView,
                0, 0);
		try {
			mBackgroundViewId = a.getResourceId(R.styleable.MaskView_backgroundView, 0);
			if(mBackgroundViewId != 0) {
				mDynamicBackground = a.getBoolean(R.styleable.MaskView_dynamicBackground, false);
				final int maskId = a.getResourceId(R.styleable.MaskView_alphaMask, 0);
				if(maskId != 0) {
					mAlphaMaskDrawable = getResources().getDrawable(maskId);
					// based on http://stackoverflow.com/a/5121922/1953590
					// this does not work in the wysiwyg editor
//					mAlphaMaskDrawable.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {
//							0, 0, 0, 0, 0,
//							0, 0, 0, 0, 0,
//							0, 0, 0, 0, 0,
//							1, 0, 0, 0, 0})));
				}
				else {
					mAlphaMaskDrawable = null;
				}				
			}
			else {
				mDynamicBackground = false;
				mAlphaMaskDrawable = null;
			}
			final int fgId = a.getResourceId(R.styleable.MaskView_foreground, 0);
			mForegroundDrawable = fgId != 0 ? getResources().getDrawable(fgId) : null;
		}
		finally {
			a.recycle();
		}
	}
	
	// secondary fields - cached bitmaps and stuff
	private final Canvas mBackgroundCanvas = new Canvas();
	private final Canvas mAlphaMaskCanvas = new Canvas();
	private final Canvas mForegroundCanvas = new Canvas();
	private final Canvas mCombinedCanvas = new Canvas();
	private final Paint mAlphaMaskPaint = new Paint();
	
	{
		mAlphaMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
	}
	
	/** Just the background. */
	private Bitmap mBackgroundBitmap;
	/** Just the alpha mask. */
	private Bitmap mAlphaMaskBitmap;
	/** Just the foreground. */
	private Bitmap mForegroundBitmap;
	/** Background with alpha applied and foreground overlaid. */
	private Bitmap mCombinedBitmap;
	
	private static final int X = 0;
	private static final int Y = 1;
	private final int[] mThisPosition = new int[2];
	private final int[] mBackgroundPosition = new int[2];
	/**
	 * This view's screen position relative to the background.  Determines
	 * what part of the background bitmap to copy.
	 */
	private final int[] mRelativePosition = new int[2];
	
	@Override
	protected void onDraw(Canvas canvas) {
		final int w = getWidth();
		final int h = getHeight();
		
		boolean combine = false;
		
		// background stuff
		if(mBackgroundViewId != 0) {
			final View backgroundView = findPeerById(mBackgroundViewId);
			if(backgroundView != null) {
				final Drawable backgroundDrawable = backgroundView.getBackground();
				if(backgroundDrawable != null) {
					// draw background if dynamic or background size changed
					final int bw = backgroundView.getWidth();
					final int bh = backgroundView.getHeight();
					if(mDynamicBackground || mBackgroundBitmap == null ||
							mBackgroundBitmap.getWidth() != bw || mBackgroundBitmap.getHeight() != bh) {
						mBackgroundBitmap = prepareBitmap(bw, bh, mBackgroundBitmap);
						mBackgroundCanvas.setBitmap(mBackgroundBitmap);
						backgroundDrawable.setBounds(0, 0, bw, bh);
						backgroundDrawable.draw(mBackgroundCanvas);
						combine = true;
					}					
					// draw alpha mask if this view's size changed
					// (only needed if background drawable exists)
					if(mAlphaMaskDrawable != null) {
						if(mAlphaMaskBitmap == null || mAlphaMaskBitmap.getWidth() != w || mAlphaMaskBitmap.getHeight() != h) {
							mAlphaMaskBitmap = prepareBitmap(w, h, mAlphaMaskBitmap);
							mAlphaMaskCanvas.setBitmap(mAlphaMaskBitmap);
							mAlphaMaskDrawable.setBounds(0, 0, w, h);
							mAlphaMaskDrawable.draw(mAlphaMaskCanvas);
							combine = true;
						}
					}				
					// set combine if relative position changed
					getLocationOnScreen(mThisPosition);
					backgroundView.getLocationOnScreen(mBackgroundPosition);
					final int x = mBackgroundPosition[X] - mThisPosition[X];
					final int y = mBackgroundPosition[Y] - mThisPosition[Y];
					if(mRelativePosition[X] != x || mRelativePosition[Y] != y) {
						mRelativePosition[X] = x;
						mRelativePosition[Y] = y;
						combine = true;
					}
				}
			}
			// TODO what if background view or its background drawable existed but was removed?
		}
		
		// draw foreground if this view's size changed
		if(mForegroundDrawable != null) {
			if(mForegroundBitmap == null || mForegroundBitmap.getWidth() != w || mForegroundBitmap.getHeight() != h) {
				mForegroundBitmap = prepareBitmap(w, h, mForegroundBitmap);
				mForegroundCanvas.setBitmap(mForegroundBitmap);
				mForegroundDrawable.setBounds(0, 0, w, h);
				mForegroundDrawable.draw(mForegroundCanvas);
				combine = true;
			}
		}
		
		if(combine) {
			mCombinedBitmap = prepareBitmap(w, h, mCombinedBitmap);
			mCombinedCanvas.setBitmap(mCombinedBitmap);
			if(mBackgroundBitmap != null) {
				mCombinedCanvas.drawBitmap(mBackgroundBitmap, mRelativePosition[X], mRelativePosition[Y], null);
				if(mAlphaMaskBitmap != null) {
					mCombinedCanvas.drawBitmap(mAlphaMaskBitmap, 0, 0, mAlphaMaskPaint);
				}
			}
			if(mForegroundBitmap != null) {
				mCombinedCanvas.drawBitmap(mForegroundBitmap, 0, 0, null);
			}
		}
		
		// finally!
		canvas.drawBitmap(mCombinedBitmap, 0,  0, null);
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
	 * @param old a bitmap to be reused, or null
	 * @return a bitmap with the correct size, possibly but not necessarily
	 * the old bitmap
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT) // for Bitmap#reconfigure(...)
	private static Bitmap prepareBitmap(int width, int height, Bitmap old) {
		if(old != null) {
			if(old.isRecycled()) {
				old = null;
			}
			else if(old.getWidth() == width && old.getHeight() == height) {
				old.eraseColor(Color.TRANSPARENT);
			}
			else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				try {
					old.reconfigure(width, height, Bitmap.Config.ARGB_8888);
					old.eraseColor(Color.TRANSPARENT);
				}
				catch(IllegalArgumentException e) {
					old.recycle(); // recommended on API 10 and lower
					old = null;
				}
			}
			else {
				old.recycle();
				old = null;
			}
		}		
		if(old == null) {
			old = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		}
		return old;
	}
	
    private View findPeerById(int resId) {
        View root = this;
        while(root.getParent() instanceof View) {
            root = (View) root.getParent();
        }
        return root.findViewById(resId);
    }

}
