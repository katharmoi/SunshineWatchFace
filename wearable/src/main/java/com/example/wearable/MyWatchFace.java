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

package com.example.wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.EmbossMaskFilter;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.view.WindowManager;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService{

    private final static String TAG = MyWatchFace.class.getSimpleName();
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
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

        private final static String MAX_TEMP = "max_temp";
        private final static String MIN_TEMP = "min_temp";
        private final static String WEATHER_ID ="weather_id";

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        private double mMax=8.0;
        private double mMin=-1.0;
        private int mWeatherId;


        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mIsRound = true;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;
        float mTextSize;
        float mClockTextSize;



        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private Paint mMinutePaint;
        private Paint mHourPaint;
        private Paint mHourLinePaint;
        private Paint mMinuteLinePaint;
        private Paint mMinuteArcPaint;
        private Paint mMiniCirclePaint;
        private Paint mHiLoTextPaint;
        private int mGradientStartColor;
        private int mGradientEndColor;
        private Paint mGradiantPaint;
        private Paint mDayTextPaint;
        private Paint mSecondsBGPaint;
        private Paint mSecondsPaint;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mTextSize = resources.getDimension(R.dimen.digital_text_size);
            mClockTextSize = resources.getDimension(R.dimen.clock_text_size);
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            float[] direction2 = new float[]{0f, -1.0f, 0.5f};
            MaskFilter filter2 = new EmbossMaskFilter(direction2, 0.8f, 15f, 1f);
            mTextPaint.setMaskFilter(filter2);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDayTextPaint = new Paint();
            mDayTextPaint.setAntiAlias(true);
            mDayTextPaint.setTextAlign(Paint.Align.CENTER);
            mDayTextPaint.setTypeface(NORMAL_TYPEFACE);
            mDayTextPaint.setColor(resources.getColor(R.color.day_text));
            mDayTextPaint.setTextSize(20f);


            mCalendar = Calendar.getInstance();

            /*Analog Clock Paints*/
            mHourPaint = new Paint();
            mHourPaint.setColor(resources.getColor(R.color.hour_hand_paint));
            mHourPaint.setStrokeWidth(5f);
            mHourPaint.setStrokeCap(Paint.Cap.SQUARE);
            mHourPaint.setAntiAlias(true);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(resources.getColor(R.color.minute_hand_paint));
            mMinutePaint.setStrokeWidth(3f);
            mMinutePaint.setStrokeCap(Paint.Cap.SQUARE);
            mMinutePaint.setAntiAlias(true);

            mHourLinePaint = new Paint();
            mHourLinePaint.setColor(resources.getColor(R.color.hour_line_color));
            mHourLinePaint.setStrokeWidth(3f);
            mHourLinePaint.setAntiAlias(true);
            mHourLinePaint.setShadowLayer(2f,0,0,resources.getColor(R.color.analag_hands_shadow));

            mMinuteLinePaint = new Paint();
            mMinuteLinePaint.setColor(resources.getColor(R.color.hour_line_color));
            mMinuteLinePaint.setStrokeWidth(1f);
            mMinuteLinePaint.setAntiAlias(true);
            mMinuteLinePaint.setShadowLayer(1f,0,0,resources.getColor(R.color.analag_hands_shadow));

            mMinuteArcPaint = new Paint();
            mMinuteArcPaint.setColor(resources.getColor(R.color.minute_arc_color));
            mMinuteArcPaint.setStrokeWidth(2f);
            mMinuteArcPaint.setAntiAlias(true);
            mMinuteArcPaint.setShadowLayer(1f,0,0,resources.getColor(R.color.analag_hands_shadow));

            /*Circle Paints*/
            mMiniCirclePaint = new Paint();
            mMiniCirclePaint.setColor(resources.getColor(R.color.hi_circle));
            //mMiniCirclePaint.setStrokeWidth(10f);
            //mMiniCirclePaint.setStyle(Paint.Style.STROKE);
            mMiniCirclePaint.setAntiAlias(true);
            float[] direction = new float[]{0f, -1f, .4f};
            MaskFilter filter = new EmbossMaskFilter(direction, .6f, 20f, 1f);
            mMiniCirclePaint.setMaskFilter(filter);

            mHiLoTextPaint = new Paint();
            mHiLoTextPaint.setTextAlign(Paint.Align.CENTER);
            mHiLoTextPaint.setAntiAlias(true);
            mHiLoTextPaint.setTextSize(resources.getDimension(R.dimen.hi_lo_text_size));
            mHiLoTextPaint.setColor(resources.getColor(R.color.hi_lo_text));

            /*Gradient bg */
            mGradientStartColor = resources.getColor(R.color.gradient_start);
            mGradientEndColor = resources.getColor(R.color.gradient_end);
            mGradiantPaint = new Paint();
            mGradiantPaint.setDither(true);

            //Seconds
            mSecondsPaint = new Paint();
            mSecondsPaint.setAntiAlias(true);
            mSecondsPaint.setStyle(Paint.Style.STROKE);
            mSecondsPaint.setColor(resources.getColor(R.color.second));
            mSecondsPaint.setStrokeWidth(3f);
            mSecondsPaint.setStrokeCap(Paint.Cap.SQUARE);

            //Seconds Bg
            mSecondsBGPaint = new Paint();
            mSecondsBGPaint.setAntiAlias(true);
            mSecondsBGPaint.setStyle(Paint.Style.STROKE);
            mSecondsBGPaint.setColor(resources.getColor(R.color.second_bg));
            mSecondsBGPaint.setStrokeWidth(4f);
            mSecondsBGPaint.setStrokeCap(Paint.Cap.SQUARE);

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
            return paint;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            RadialGradient gradient = new RadialGradient(width/2,
                    height/2,
                    width/2,
                    mGradientStartColor,
                    mGradientEndColor, Shader.TileMode.CLAMP);
            mGradiantPaint.setShader(gradient);
            super.onSurfaceChanged(holder,format,width,height);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                if(mGoogleApiClient != null && mGoogleApiClient.isConnected()){
                    Wearable.DataApi.removeListener(mGoogleApiClient,this);
                    mGoogleApiClient.disconnect();
                }
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
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            mTextPaint.setTextSize(textSize);
            mIsRound = isRound;
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
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
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
               // canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                drawGradientBg(canvas,bounds);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            drawDigitalClock(canvas,bounds);

            if(!mAmbient){
                drawHi(canvas,bounds,Integer.toString((int)mMax));
                drawLow(canvas,bounds,Integer.toString((int)mMin));
                drawWeather(canvas,bounds);
                drawAnalogClock(canvas,bounds,mCalendar);
                drawSecond(canvas,bounds);
            }
        }

        /*Analog Clock Draw Helper method*/
        private void drawAnalogClock(Canvas canvas,Rect bounds,Calendar calendar){

            final float TWO_PI = (float)Math.PI *2f;

            int width = bounds.width();
            int height = bounds.height();

            float cX = width/2f;
            float cY = height/2f;

           /*Draw Hour &Minute HAnd*/
            float seconds = calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND)/1000f;

            float minutes = mCalendar.get(Calendar.MINUTE) + seconds/60f;
            float minRotation= minutes/60f * TWO_PI;

            float hours = mCalendar.get(Calendar.HOUR) + minutes/60f;
            float hoursRotation = hours * (TWO_PI/12f);

            float minLength = cX -40;
            float hourLength = cX -80;



            float minX = (float) (minLength * Math.sin(minRotation));
            float minY = (float)(minLength *-Math.cos(minRotation));
            canvas.drawLine(cX,cY,cX + minX, cY + minY, mMinutePaint);

            float hourX = (float) (hourLength * Math.sin(hoursRotation));
            float hourY =(float)(hourLength * -Math.cos(hoursRotation));
            canvas.drawLine(cX,cY,cX + hourX, cY + hourY,mHourPaint);

            /*Draw Hour and minute indicators*/

            float hourLineHeight = cX - 12;
            float minuteLineHeight= cX -5;
            for (int i = 0; i < 60; i++) {
                float tickRot = i * TWO_PI / 60;
                Paint paint = (i%5)==0 ? mHourLinePaint: mMinuteLinePaint;
                float h = (i%5)==0 ? hourLineHeight: minuteLineHeight;
                float innerX = (float) Math.sin(tickRot) * h;
                float innerY = (float) -Math.cos(tickRot) * h;
                float outerX = (float) Math.sin(tickRot) * cX;
                float outerY = (float) -Math.cos(tickRot) * cX;
                canvas.drawLine(cX + innerX, cY + innerY,
                        cX + outerX, cY + outerY, paint);
            }
            /*Draw center circle*/
            canvas.drawCircle(cX,cY,8f,mTextPaint);

        }

        private void drawDigitalClock(Canvas canvas,Rect bounds){
            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%02d:%02d",
                    mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE));

            Rect textBounds = new Rect();
            mTextPaint.getTextBounds(text,0,text.length(),textBounds);
            int xOffset = textBounds.width()/2;
            int yOffset = textBounds.height()/2 + bounds.height()/20;
            float margin = 5f;

            canvas.drawText(text, bounds.width()/2 -xOffset, bounds.height()/2 -yOffset, mTextPaint);
            if(!mAmbient){
                SimpleDateFormat dF = new SimpleDateFormat("EE");
                String day = dF.format(mCalendar.getTime()).toUpperCase();
                Rect dayTextBounds = new Rect();
                mDayTextPaint.getTextBounds(day,0,day.length(),dayTextBounds);
                canvas.drawText(day,
                        bounds.width()/2,
                        bounds.height()/2 - textBounds.height() - bounds.height()/20 - dayTextBounds.height() -margin ,
                        mDayTextPaint);
            }

        }

        private void drawSecond(Canvas canvas,Rect bounds){
            float padding = 15f;
            float seconds = mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND)/1000f;
            float secondsRot = seconds*6f;
            RectF circleBounds = new RectF(bounds.left + padding,
                    bounds.top + padding,
                    bounds.right - padding,
                    bounds.bottom - padding
                    );
            canvas.drawArc(circleBounds,-90,secondsRot,false,mSecondsPaint);

        }

        private void drawHi(Canvas canvas, Rect bounds,String hi){
            float cX = bounds.width()/3;
            float cY = bounds.height()*0.65f;
            canvas.drawCircle(cX,cY,bounds.width()/12,mMiniCirclePaint);
            Rect textBounds = new Rect();
            mHiLoTextPaint.getTextBounds(hi,0,hi.length(),textBounds);
            int yOffset= textBounds.height()/2;
            String formatted = hi +(char) 0x00B0;
            canvas.drawText(formatted,cX,cY+yOffset,mHiLoTextPaint);
        }

        private void drawLow(Canvas canvas, Rect bounds,String low){
            float cX = 2*bounds.width()/3;
            float cY = bounds.height()*0.65f;
            canvas.drawCircle(cX,cY,bounds.width()/12,mMiniCirclePaint);
            Rect textBounds = new Rect();
            mHiLoTextPaint.getTextBounds(low,0,low.length(),textBounds);
            int yOffset= textBounds.height()/2;
            String formatted = low +(char) 0x00B0;
            canvas.drawText(formatted,cX,cY+yOffset,mHiLoTextPaint);
        }

        private void drawGradientBg(Canvas canvas,Rect bounds){
            if(mIsRound){
                canvas.drawCircle(bounds.centerX(),bounds.centerY(),bounds.width(),mGradiantPaint);
            }else{
                canvas.drawRect(bounds,mGradiantPaint);
            }
        }


        private void drawWeather(Canvas canvas,Rect bounds){
            float cX = bounds.centerX();
            float cY = bounds.height()*0.8f;
            canvas.drawCircle(cX,cY,bounds.width()/12,mMiniCirclePaint);
            Drawable icon = getResources().getDrawable(getSmallArtResourceIdForWeatherCondition(mWeatherId));
            Bitmap iconBitmap = ((BitmapDrawable) icon).getBitmap();
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(iconBitmap,30,30,true);
            canvas.drawBitmap(scaledBitmap,
                    cX - scaledBitmap.getWidth()/2,
                    cY- scaledBitmap.getHeight()/2
                    ,null);
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
            return isVisible() && !isInAmbientMode();
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
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG,"wear side connected");
            Wearable.DataApi.addListener(mGoogleApiClient,this);

        }

        @Override
        public void onConnectionSuspended(int i) {
        }


        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for(DataEvent event :dataEventBuffer){
                if(event.getType() == DataEvent.TYPE_CHANGED){
                    DataItem dataItem = event.getDataItem();
                    if(dataItem.getUri().getPath().compareTo("/weather") == 0){
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        mMax = dataMap.getDouble(MAX_TEMP);
                        mMin = dataMap.getDouble(MIN_TEMP);
                        mWeatherId = dataMap.getInt(WEATHER_ID);
                    }
                }
            }

        }

        public  int getSmallArtResourceIdForWeatherCondition(int weatherId) {

        /*
         * Based on weather code data for Open Weather Map.
         */
            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.ic_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.ic_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.ic_rain;
            } else if (weatherId == 511) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.ic_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.ic_fog;
            } else if (weatherId == 761 || weatherId == 771 || weatherId == 781) {
                return R.drawable.ic_storm;
            } else if (weatherId == 800) {
                return R.drawable.ic_clear;
            } else if (weatherId == 801) {
                return R.drawable.ic_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.ic_cloudy;
            } else if (weatherId >= 900 && weatherId <= 906) {
                return R.drawable.ic_storm;
            } else if (weatherId >= 958 && weatherId <= 962) {
                return R.drawable.ic_storm;
            } else if (weatherId >= 951 && weatherId <= 957) {
                return R.drawable.ic_clear;
            }

            Log.e(TAG, "Unknown Weather: " + weatherId);
            return R.drawable.ic_storm;
        }
    }
}
