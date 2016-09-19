/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.sunshinewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.android.sunshine.app.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunShineWatchFace extends CanvasWatchFaceService
{
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }



    private static class EngineHandler extends Handler {
        private final WeakReference<SunShineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunShineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunShineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements DataApi.DataListener, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTimeTextPaint;
        Paint mDateTextPaint;
        Paint mLowTempTextPaint;
        Paint mHighTempTextPaint;
        boolean mAmbient;
        Calendar mCalendar;
        private final String ACTION = "com.example.TODAY_WEATHER";

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };


        int mTapCount;

        float mXOffset;
        float mYOffset;

        float mLineHeight;
        private final String PATH = "/today-weather";

        private GoogleApiClient mGoogleApiClient;
        private String mHighTemp="1";
        private String mLowTemp="0";
        private int mWeatherId=800;

        SimpleDateFormat mDateFormat;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(SunShineWatchFace.this)
                    .addOnConnectionFailedListener(this)
                    .addConnectionCallbacks(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();


            setWatchFaceStyle(new WatchFaceStyle.Builder(SunShineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunShineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mDateFormat = new SimpleDateFormat("EEE, MMM dd yyyy");


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimeTextPaint = new Paint();
            mTimeTextPaint = createTextPaint(resources.getColor(R.color.text_primary));

            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(resources.getColor(R.color.text_secondary));

            mHighTempTextPaint = new Paint();
            mHighTempTextPaint = createTempTextPaint(resources.getColor(R.color.text_primary));

            mLowTempTextPaint = new Paint();
            mLowTempTextPaint = createTempTextPaint(resources.getColor(R.color.text_secondary));
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            return paint;
        }

        private Paint createTempTextPaint(int textColor){
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunShineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);

        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunShineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunShineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);
            float tempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);

            mLineHeight = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);

            mTimeTextPaint.setTextSize(timeTextSize);
            mDateTextPaint.setTextSize(dateTextSize);
            mLowTempTextPaint.setTextSize(tempTextSize);
            mHighTempTextPaint.setTextSize(tempTextSize);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

//            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
//            mTimeTextPaint.setTypeface(burnInProtection? NORMAL_TYPEFACE : Typeface.DEFAULT_BOLD);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunShineWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mCalendar = Calendar.getInstance();
//            String text = mAmbient
//                    ?String.format("%d:%02d",mCalendar.get(Calendar.HOUR_OF_DAY), mCalendar.get(Calendar.MINUTE))
//                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
//
            String text = String.format("%d:%02d",mCalendar.get(Calendar.HOUR_OF_DAY), mCalendar.get(Calendar.MINUTE));

            //Time & Date
            canvas.drawText(text, canvas.getWidth()/2, mYOffset, mTimeTextPaint);
            canvas.drawText(mDateFormat.format(mCalendar.getTime()).toUpperCase(), canvas.getWidth()/2, (float)(mYOffset+mLineHeight*1.5), mDateTextPaint);

            //Icon
            Bitmap icon = BitmapFactory.decodeResource(getResources(), IconGetter.getIconResourceForWeatherCondition(mWeatherId));
            canvas.drawBitmap(icon, mXOffset, mYOffset+mLineHeight*3, null);

            //Temperature
            Log.e("ICON WIDTH", String.valueOf(icon.getWidth()));
            canvas.drawText(mHighTemp, mXOffset + icon.getWidth()+10, mYOffset+mLineHeight*3 + icon.getHeight()-15, mHighTempTextPaint);
            canvas.drawText(mLowTemp, mXOffset+ icon.getWidth()+ 80, mYOffset+mLineHeight*3+icon.getHeight()-15, mLowTempTextPaint);





        }


        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);
            Log.e("ONCONNECTED :::" , "Conection Successful");
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.v("DATACHANGED :::", "method called");
            for(DataEvent dataEvent : dataEvents){
                if(dataEvent.getType() == DataEvent.TYPE_CHANGED){
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    Log.v("DATACHANGED :::", "inside for and if statement");
                    if(path.equals(PATH)){
                        Log.v("DATACHANGED :::", "PAth equals");
                        mLowTemp = dataMap.getString("lowTemp");
                        mHighTemp = dataMap.getString("highTemp");
                        mWeatherId = dataMap.getInt("weatherId");
                        Log.e("WEATHER ID", String.valueOf(mWeatherId));
                        Log.e("LOW TEMPERATURE", mLowTemp);
                        Log.e("HIGH TEMPERATURE", mHighTemp);
                        invalidate();
                    }

                }
            }
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {

        }
    }
}
