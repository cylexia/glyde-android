package com.cylexia.mobile.glyde;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.cylexia.mobile.glyde.glue.ExtFrontEnd;

/**
 * Created by sparx104 on 20/04/2015.
 */
public class FrontEndView extends ImageView implements View.OnClickListener {
	private ExtFrontEnd ext;
	private ViewActivity view;
	private int focusIndex;

    /**
     * Simple constructor to use when creating a view from code.
     *
     * @param context The ViewActivity the view is running in, through which it can
     *                access the current theme, resources, etc. as well as callbacks for Glue events
     */
    public FrontEndView( ViewActivity context, ExtFrontEnd e ) {
        super( context );
		this.ext = e;
		this.view = context;
		this.focusIndex = -1;
		setFocusable( true );
		setFocusableInTouchMode( true );
		setAdjustViewBounds( true );
		setScaleType( ScaleType.FIT_START );
    }

    @Override
    protected void onDraw( Canvas canvas ) {
		super.onDraw(canvas);
    }

	@Override
	public boolean onTouchEvent( MotionEvent event ) {
		switch( event.getAction() ) {
			case MotionEvent.ACTION_DOWN:
				return true;
			case MotionEvent.ACTION_UP:
				// TODO: scaling doesn't take into account the image position - it distorts the aspect (see tablet in portrait)
				String label = getLabelForButtonAtScaledXY( event.getX(), event.getY() );
				if( label != null ) {
					view.runScriptAndSync( label );
				}
		}
		return super.onTouchEvent( event );
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		boolean handled = handleKeyEvent( keyCode, event, false );
		return (handled || super.onKeyUp( keyCode, event ));
	}

	@Override
	public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
		boolean handled = handleKeyEvent( keyCode, event, true );
		return (handled || super.onKeyMultiple( keyCode, repeatCount, event ));
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		// this is for the controllers' sticks
		return super.onGenericMotionEvent( event );
	}

	/**
	 * Called when a view has been clicked.
	 *
	 * @param v The view that was clicked.
	 */
	@Override
	public void onClick( View v ) {
		if( v == this ) {
			Toast.makeText( getContext(), "click", Toast.LENGTH_SHORT ).show();
		}
	}

	private String getLabelForButtonAtScaledXY( float x, float y ) {
		float sx = ((float)getWidth() / (float)ext.getWindowWidth());
		float sy = ((float)getHeight() / (float)ext.getWindowHeight());
		float scale_factor = (sx < sy) ? sx : sy;
		int px = (int)(x / scale_factor);
		int py = (int)(y / scale_factor);
		//int w = getWidth();
		//int sw = (int)(getWidth() / scale_factor);
		//Log.i( "", "w" + w + "," + sw);
		return ext.getLabelForButtonAt( px, py );
	}

	private boolean handleKeyEvent( int keyCode, KeyEvent event, boolean repeat ) {
		if( (event.getAction() == KeyEvent.ACTION_UP) || repeat ) {
			switch( keyCode ) {
				case KeyEvent.KEYCODE_DPAD_UP:
				case KeyEvent.KEYCODE_DPAD_LEFT:
					focusPrevious();
					return true;

				case KeyEvent.KEYCODE_DPAD_DOWN:
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					focusNext();
					return true;

				case KeyEvent.KEYCODE_DPAD_CENTER:
				case KeyEvent.KEYCODE_BUTTON_A:
					focusAction();
					return true;
			}
		}
		return false;
	}

	public void syncUI() {
		Bitmap bmp = createFocusedBitmap();
		if( bmp != null ) {
			// we do our scaling here
			// figure out the scale we need here:
			Bitmap scaled = Bitmap.createScaledBitmap( bmp, bmp.getWidth(), bmp.getHeight(), false );
			setImageBitmap( scaled );
		}
	}

	private Bitmap createFocusedBitmap() {
		if( focusIndex > -1 ) {
			Rect r = ext.getButtonRectAtIndex( focusIndex );
			if( r != null ) {
				Bitmap temp = Bitmap.createBitmap( ext.getBitmap() );
				Canvas g = new Canvas( temp );
				Paint p = new Paint();
				p.setColor( Color.parseColor( "#990077ff" ) );
				p.setStrokeWidth( 5 );
				p.setStyle( Paint.Style.STROKE );
				g.drawRect( r, p );
				return temp;
			}
		}
		return ext.getBitmap();
	}

	public void focusNext() {
		if( (focusIndex == -1) || (focusIndex >= (ext.getButtonSize() - 1)) ) {
			this.focusIndex = 0;
		} else {
			focusIndex++;
		}
		syncUI();
	}

	public void focusPrevious() {
		if( focusIndex <= 0 ) {
			this.focusIndex = (ext.getButtonSize() - 1);
		} else {
			focusIndex--;
		}
		syncUI();
	}

	public void focusAction() {
		if( (focusIndex >= 0) && (focusIndex < ext.getButtonSize()) ) {
			String label = ext.getButtonLabelAtIndex( focusIndex );
			if( label != null ) {
				view.runScriptAndSync( label );
			}
		}
	}

}
