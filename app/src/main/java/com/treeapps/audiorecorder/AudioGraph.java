package com.treeapps.audiorecorder;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;


public class AudioGraph extends ImageView {

    private static final String PAGE_COLOR = "#e0bc1d";
    private static final String GRAPH_BORDER_COLOR = "#e0bc1d"; // "#bbbbbb";
    private static final String GRAPH_FILL_COLOR = "#cccccc";
    private static final String TAG = "AudioGraph";

    Context context;
    boolean boolIsInitializing;




    public interface OnInitCompleteListener {
        public void onComplete();
    }

    public interface OnPageChangedListener {
        public void onChanged(PageValue pageValue);
    }

    public interface OnScreenSizeChangedListener {
        public void onChanged(PageValue pageValue);
    }

    public interface OnEndCursorChangedListener {
        public int[] getSinglePageAfterCursorBuffer(double fltPercent);
    }

    private int intPageAmount = 1;
    private long lngTimelineDataAmountInRmsFrames;

    public class PageValue {
        public double fltPageNum = 1;
        public long lngDataAmountInRmsFrames = 0;
        public double fltPlayPercent = 0;
        public double fltStartPercent = 0;
        public double fltEndPercent = 0;


        /**
         *
         * @param fltPageNum - Indicates the position of the page start, 1.0 -> intPageAmount
         * @param lngDataAmount - Indicates how many Rms frames are in the complete timeline
         * @param fltPlayPercent - Indicates the position of the Timeline play cursor position, 0.0 to 100.0 %
         */
        public PageValue(double fltPageNum, long lngDataAmount, double fltPlayPercent, double fltStartPercent, double fltEndPercent) {
            this.fltPageNum = fltPageNum;
            this.lngDataAmountInRmsFrames = lngDataAmount;
            this.fltPlayPercent = fltPlayPercent;
            this.fltStartPercent = fltStartPercent;
            this.fltEndPercent = fltEndPercent;
        }

        public PageValue copy() {
            return new PageValue(this.fltPageNum, this.lngDataAmountInRmsFrames, this.fltPlayPercent, this.fltStartPercent, this.fltEndPercent);
        }

        public int getCurrentPage() {
            return (int) fltPageNum;
        }

        /**
         * Needs to be fast because its being used in onDraw
         * @return
         */
        private String getFinalTimeString() {
            double fltTime = (double)(lngTimelineDataAmountInRmsFrames * fltRmsFramePeriodInMs)/1000.0f;
            return new DecimalFormat("000.00").format(fltTime);
        }

        /**
         * Needs to be fast because its being used in onDraw
         * @return
         */
        private String getPlayTimeString() {
            double fltPlayPositionInFrames = (fltPlayPercent/100.0f) * lngTimelineDataAmountInRmsFrames;
            double fltTime = (double)(fltPlayPositionInFrames * fltRmsFramePeriodInMs)/1000.0f;
            return new DecimalFormat("000.00").format(fltTime); // String.format("%3.1f", fltTime);
        }

    }
    private OnInitCompleteListener onInitCompleteListener;
    private OnPageChangedListener onPageChangedListener;
    private OnScreenSizeChangedListener onScreenSizeChangedListener;
    private OnEndCursorChangedListener onEndCursorChangedListener;

    // All values in px unless stated
    private int intPageSizeInMs = 1000;
    private static int intPageSizeInRmsFrames = 0;


    Paint paintDefault;
    Paint paintTimeline;
    Paint paintGraphBorder;
    Paint paintGraphFill;
    Paint paintWave;
    Paint paintTime;

    private final Bitmap bmpCursorNormal = BitmapFactory.decodeResource(getResources(), R.mipmap.seek_thumb_normal);
    private final Bitmap bmpCursorPressed = BitmapFactory.decodeResource(getResources(), R.mipmap.seek_thumb_pressed);
    private final Bitmap bmpCursorHidden = BitmapFactory.decodeResource(getResources(), R.mipmap.seek_thumb_hidden);
    private final Bitmap bmpPageCursorNormal = BitmapFactory.decodeResource(getResources(), R.mipmap.page_thumb_normal);
    private final Bitmap bmpPageCursorPressed = BitmapFactory.decodeResource(getResources(), R.mipmap.page_thumb_pressed);



    private int intThumbWidth;
    private int intThumbHalfWidth;

    private int intThumbHeight;
    private int intThumbHalfHeight;

    private int intWidgetWidth;
    private int intWidgetHeight;

    // Cursor objects
    private CursorGraphPlay cursorGraphPlay;
    private CursorGraphStart cursorGraphStart;
    private CursorGraphEnd cursorGraphEnd;
    private CursorTimelinePlay cursorTimelinePlay;
    private CursorTimelinePage cursorTimelinePage;
    private CursorTimelineStart cursorTimelineStart;
    private CursorTimelineEnd cursorTimelineEnd;
    ArrayList<Hotspot> hotSpots;

    // Items locations
    private int intPaddingVert;

    private RectF rectGraph;

    private PointF pointTimelineCursorPlay;
    private PointF pointTimelineCursorStart;
    private PointF pointTimelineCursorEnd;

    private int intTimeHeight;
    private int intTimeHalfHeight;
    private int intTimeWidth;
    private int intTimeHalfWidth;
    private RectF rectTimeStart;
    private RectF rectTimeEnd;
    private RectF rectTimePlay;

    private PointF pointTimelineStart;
    private PointF pointTimelineEnd;

    // Scrolling
    private static final int ACTION_POINTER_UP = 0x6, ACTION_POINTER_INDEX_MASK = 0x0000ff00,
            ACTION_POINTER_INDEX_SHIFT = 8;
    private static final int INVALID_POINTER_ID = 255;
    private int mScaledTouchSlop;
    private float mDownMotionX;
    private float mDownMotionY;
    private int mActivePointerId = INVALID_POINTER_ID;
    private Hotspot hotspotPressed;
    private boolean mIsDragging;

    // Paging
    private PageValue pageValue = new PageValue(1, 0, 0, 0, 100);

    // Graph
    private int[] intGraphRawValues;
    private int[] intAfterEndCursorRawValues;
    private float[] fltGraphNormalizedValues;
    private float[] floatGraphPoints = new float[8];
    private int intGraphValuesMax = 0;
    private double fltRmsFramePeriodInMs;



    private interface OnCursorChanged {
        public void OnValueChange(double fltValue);
    }


    public AudioGraph(Context context) {
        super(context);
        this.context = context;
        boolIsInitializing = true;
        init();
    }

    public AudioGraph(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs,R.styleable.audio_graph);
        CharSequence s = a.getString(R.styleable.audio_graph_page_size_in_ms);
        if (s != null) {
            this.intPageSizeInMs = Integer.parseInt(s.toString());
        }
        a.recycle();

        this.context = context;
        boolIsInitializing = true;
        init();

    }

    public AudioGraph(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs,R.styleable.audio_graph);
        CharSequence s = a.getString(R.styleable.audio_graph_page_size_in_ms);
        if (s != null) {
            this.intPageSizeInMs = Integer.parseInt(s.toString());
        }
        a.recycle();

        this.context = context;
        boolIsInitializing = true;
        init();
    }

    public void setPageValue(PageValue pageValueNew) {
        pageValue = pageValueNew;

        cursorTimelinePage.setValue(pageValue);

        // Update slow changing values
        if (pageValue.lngDataAmountInRmsFrames == 0) {
            intPageAmount = 1;
        } else {
            intPageAmount = (int) Math.ceil((double) pageValue.lngDataAmountInRmsFrames /intPageSizeInRmsFrames);
        }
        lngTimelineDataAmountInRmsFrames = intPageAmount * intPageSizeInRmsFrames;
        invalidate();
    }

    public void setPageSizeInMs(int intPageSizeInMs) {
        this.intPageSizeInMs =intPageSizeInMs;
        this.fltRmsFramePeriodInMs = ((double)this.intPageSizeInMs)/this.intPageSizeInRmsFrames;
    }

    public void setOnInitCompleteListener(OnInitCompleteListener onInitCompleteListener) {
        this.onInitCompleteListener = onInitCompleteListener;
    }

    public void setOnPageChangedListener (OnPageChangedListener onPageChangedListener) {
        this.onPageChangedListener = onPageChangedListener;
    }

    public void setOnScreenSizeChangedListener (OnScreenSizeChangedListener onScreenSizeChangedListener) {
        this.onScreenSizeChangedListener = onScreenSizeChangedListener;
    }

    public void setOnEndCursorChangedListener (OnEndCursorChangedListener onEndCursorChangedListener) {
        this.onEndCursorChangedListener = onEndCursorChangedListener;
    }

    public long percentToRmsFrame(double fltPercent, long lngDataSizeInPcmShorts, int intAudioSampleRate) {
        int intPageAmount = (int) Math.ceil((double)lngDataSizeInPcmShorts/(intPageSizeInRmsFrames * getOptimalDataSampleBufferSizeInShortsAccurate(intAudioSampleRate)) );
        long lngTimelineLenInRmsFrames = intPageAmount * intPageSizeInRmsFrames;
        long lngConvertedToRmsFrames = (long) ((fltPercent/100) * lngTimelineLenInRmsFrames);
        return lngConvertedToRmsFrames;
    }

    public long percentToShort(double fltPercent, long lngDataSizeInPcmShorts, int intAudioSampleRate) {
        long lngConvertedToRmsFrames = percentToRmsFrame(fltPercent, lngDataSizeInPcmShorts, intAudioSampleRate);
        long lngConvertedToShorts = (long) (lngConvertedToRmsFrames * getOptimalDataSampleBufferSizeInShortsAccurate(intAudioSampleRate));
        return lngConvertedToShorts;
    }

    public long percentToByte(double fltPercent, long lngDataSizeInPcmShorts, int intAudioSampleRate) {
        return percentToShort(fltPercent, lngDataSizeInPcmShorts, intAudioSampleRate) * 2;
    }

    public void enablePlayCursor(boolean boolIsEnabled) {
        cursorGraphPlay.boolIsEnabled = boolIsEnabled;
        cursorTimelinePlay.boolIsEnabled = boolIsEnabled;
        invalidate();
    }


    public double getDataAmountAsPercent() {
        long lngTimelineLenInRmsFrames = intPageAmount * intPageSizeInRmsFrames;
        return ((double)pageValue.lngDataAmountInRmsFrames /lngTimelineLenInRmsFrames) * 100.0f;
    }

    public boolean isPercentEndOfFile(double fltPercent) {
        double fltDataDataAmountAsPercent = getDataAmountAsPercent();
        if (Math.abs(fltPercent - fltDataDataAmountAsPercent) < 0.1) {
            return true;
        }
        return false;
    }

    public boolean isPlayCursorAtEndOfFile() {
        return isPercentEndOfFile(pageValue.fltPlayPercent);
    }

    public double getValueAsPercentInPage(double fltValueAsPercentInTimeline) {

        // Get where page start/end aligns with item position
        double fltTimeLineRangeInRmsFrames = intPageAmount * intPageSizeInRmsFrames;
        double fltPageHorStartPositionInTimeline = ((pageValue.fltPageNum - 1) * intPageSizeInRmsFrames);
        double fltPageHorEndPositionInTimeline = (pageValue.fltPageNum * intPageSizeInRmsFrames);
        double fltItemPositionInTimeline = (fltValueAsPercentInTimeline/100.0f) * fltTimeLineRangeInRmsFrames;

        double fltValue = 0;
        if ((fltItemPositionInTimeline >= fltPageHorStartPositionInTimeline) && (fltItemPositionInTimeline <= fltPageHorEndPositionInTimeline)) {
            // Selected position is inside display page
            fltValue =  ((fltItemPositionInTimeline - fltPageHorStartPositionInTimeline) * 100.0f)/intPageSizeInRmsFrames;
        } else if (fltItemPositionInTimeline < fltPageHorStartPositionInTimeline){
            // Selected position is outside on left of page
            fltValue = 0;
        } else if (fltItemPositionInTimeline > fltPageHorEndPositionInTimeline){
            // Selected position is outside on right of page
            fltValue = 100;
        }
        return fltValue;
    }

    public void setPlayCursorToEndOfFile() {
        double fltDataDataAmountAsPercent = getDataAmountAsPercent();
        pageValue.fltPlayPercent = fltDataDataAmountAsPercent;
    }

    public PageValue getPageValue() {
        return pageValue;
    }





    public PageValue updatePageValueToDisplayEndPage(PageValue pageValueOrig, long lngDataAmountInShorts, int intAudioSampleRate) {
        PageValue pageValueNew = pageValueOrig.copy();
        if (pageValue.lngDataAmountInRmsFrames != 0) {
            pageValueNew.fltPlayPercent = getDataAmountAsPercent();
            pageValueNew.fltPageNum = intPageAmount;
        } else {
            pageValueNew.fltPlayPercent = 100;
        }
        return pageValueNew;
    }

    public int getPageSizeInRmsFrames() {
        return intPageSizeInRmsFrames;
    }


    private void init() {

        if (isInEditMode()) return;

        paintDefault = new Paint();
        paintDefault.setStrokeWidth(3f);
        paintDefault.setAntiAlias(true);
        paintDefault.setColor(Color.BLUE);
        paintDefault.setStyle(Paint.Style.STROKE);
        paintDefault.setTextSize(spToPixels(context, 13));

        paintTimeline = new Paint();
        paintTimeline.setStrokeWidth(dpToPx(2));
        paintTimeline.setAntiAlias(true);
        paintTimeline.setColor(Color.parseColor(PAGE_COLOR));
        paintTimeline.setStyle(Paint.Style.STROKE);

        paintGraphBorder = new Paint();
        paintGraphBorder.setStrokeWidth(dpToPx(2));
        paintGraphBorder.setAntiAlias(true);
        paintGraphBorder.setColor(Color.parseColor(GRAPH_BORDER_COLOR));
        paintGraphBorder.setStyle(Paint.Style.STROKE);

        paintGraphFill = new Paint();
        paintGraphFill.setAntiAlias(true);
        paintGraphFill.setColor(Color.parseColor(GRAPH_FILL_COLOR));
        paintGraphFill.setStyle(Paint.Style.FILL);

        paintWave = new Paint();
        paintWave.setStrokeWidth(dpToPx(4));
        paintWave.setAntiAlias(true);
        paintWave.setColor(Color.BLUE);
        paintWave.setStyle(Paint.Style.STROKE);

        // Calculate item dimensions
        paintTime = new Paint();
        paintTime.setColor(Color.BLUE);
        paintTime.setTextSize(spToPixels(context, 11));
        paintTime.setStyle(Paint.Style.FILL);

        intTimeHeight = getTextHeight("000.00", paintTime);
        intTimeHalfHeight = intTimeHeight/2;
        intTimeWidth = getTextWidth("000.00", paintTime);
        intTimeHalfWidth = intTimeWidth/2;
        rectTimeStart = new RectF(0,0, intTimeHeight, intTimeWidth);
        rectTimeEnd = new RectF(rectTimeStart);
        rectTimePlay =  new RectF(rectTimeStart);

        intThumbWidth = bmpCursorNormal.getWidth();
        intThumbHalfWidth = intThumbWidth/2;

        intThumbHeight = bmpCursorNormal.getWidth();
        intThumbHalfHeight = intThumbHeight/2;

        intWidgetHeight = (int)(intThumbHalfHeight + (3 * intThumbHeight) + (2 * intThumbHeight) + (1.5 * intThumbHeight));

        intPaddingVert = intThumbHalfWidth;

        // Scrolling
        mScaledTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

    }

    private void initItemLocations() {

        if (isInEditMode()) return;

        // Items locations
        // Static items
        rectGraph = new RectF();
        rectGraph.left = intThumbHalfWidth;
        rectGraph.top = intThumbHalfWidth;
        rectGraph.right = intWidgetWidth - intThumbHalfWidth;
        rectGraph.bottom =  rectGraph.top + (3 * intThumbHeight );

        pointTimelineStart = new PointF(intThumbHalfWidth, rectGraph.bottom + 2 * intThumbHeight + intTimeHeight);
        pointTimelineEnd = new PointF(intWidgetWidth - intThumbHalfWidth, pointTimelineStart.y);
        rectTimeStart.offsetTo(0, pointTimelineStart.y + intThumbHeight + intThumbHalfHeight + 2 * intTimeHalfHeight );
        rectTimeEnd.offsetTo(intWidgetWidth - intTimeWidth, pointTimelineStart.y + intThumbHeight + intThumbHalfHeight + 2 * intTimeHalfHeight );


        // Dynamic items
        hotSpots = new ArrayList<Hotspot>();
        cursorGraphPlay = new CursorGraphPlay(bmpCursorNormal,bmpCursorPressed, HotSpotType.CENTER, null);
        hotSpots.add(cursorGraphPlay);
        cursorGraphStart = new CursorGraphStart(bmpCursorNormal,bmpCursorPressed, HotSpotType.MIN, null);
        hotSpots.add(cursorGraphStart);
        cursorGraphEnd = new CursorGraphEnd(bmpCursorNormal,bmpCursorPressed, HotSpotType.MAX, null);
        hotSpots.add(cursorGraphEnd);
        cursorTimelinePlay  = new CursorTimelinePlay(bmpCursorNormal,bmpCursorPressed, HotSpotType.CENTER, null);
        hotSpots.add(cursorTimelinePlay);
        cursorTimelinePage  = new CursorTimelinePage(bmpPageCursorNormal,bmpPageCursorPressed, HotSpotType.CENTER, new OnCursorChanged() {
            @Override
            public void OnValueChange(double fltValue) {
                onPageChangedListener.onChanged(pageValue);
            }
        });
        hotSpots.add(cursorTimelinePage);
        cursorTimelineStart  = new CursorTimelineStart(bmpCursorNormal,bmpCursorPressed, HotSpotType.MIN, null);
        hotSpots.add(cursorTimelineStart);
        cursorTimelineEnd  = new CursorTimelineEnd(bmpCursorNormal,bmpCursorPressed, HotSpotType.MAX, null);
        hotSpots.add(cursorTimelineEnd);

        // Determine total height
        intWidgetHeight = (int) (pointTimelineStart.y + intThumbHeight + intThumbHalfHeight + 2 * intTimeHalfHeight);

        this.intPageSizeInRmsFrames = (int)rectGraph.width();

        cursorGraphPlay.setValue(cursorGraphPlay.getValue());
        cursorGraphStart.setValue(cursorGraphStart.getValue());
        cursorGraphEnd.setValue(cursorGraphEnd.getValue());

        cursorGraphPlay.setValue(cursorGraphPlay.getValue());
        cursorTimelinePlay.setValue(cursorTimelinePlay.getValue());
        cursorTimelinePage.setValue(cursorTimelinePage.getValue()) ;
        cursorTimelineStart.setValue(cursorTimelineStart.getValue());
        cursorTimelineEnd.setValue(cursorTimelineEnd.getValue());

    }


    private int intGraphPageAmount;

    public void clearGraph (){
        intGraphRawValues = null;
        intAfterEndCursorRawValues = null;
        fltGraphNormalizedValues = null;
        intGraphPageAmount = 1;

        cursorGraphPlay.setValue(0);
        cursorGraphStart.setValue(0);
        cursorGraphEnd.setValue(100);

        cursorTimelinePlay.setValue(0);
        pageValue = new PageValue(1, 0, 0, 0, 100);
        cursorTimelinePage.setValue(pageValue) ;
        cursorTimelineStart.setValue(0);
        cursorTimelineEnd.setValue(100);
    }


    /**
     * updateGraph - Concatenate and display up to a short page of data
     */
    public interface OnGraphErrorListener {
        public void onError(String strErrorMessage);
    }

    public void updateGraph (int[] intGraphNewValues) {
        intGraphValuesMax = 0;
        intGraphRawValues = null;

        intGraphRawValues = new int[intGraphNewValues.length];
        System.arraycopy(intGraphNewValues, 0, intGraphRawValues,0, intGraphNewValues.length);

        // Find amplitude of new data
        int intMaxValue = 0;
        for (int i = 0; i < intGraphRawValues.length; i++) {
            if (intGraphRawValues[i] > intMaxValue) {
                intMaxValue = intGraphRawValues[i];
            }
        }

        // Normalise the complete raw graph;
        fltGraphNormalizedValues = new float[intGraphRawValues.length];

        for (int i = 0; i < intGraphRawValues.length; i++) {
            fltGraphNormalizedValues[i] = (intGraphRawValues[i] * 95.0f) / intMaxValue;
        }

        invalidate();

    }

    /**
     * Add new buffer data after the current play cursor
     * Clear current data up to the end cursor (if the end cursor is in the same page)
     * otherwise clear up to the end of the page
     * If the end cursor is reached (if on same page), insert new data at current play and end cursor position (moving together),
     * effectively moving all data after end cursor out to the right.
     * If the end cursor is to be displayed on the page, add data after end cursor to fill the rest of the page. This data
     * comes from a buffer of data captured (only one page long) as soon as the end cursor moves away from the end.
     *
     * @param intUpdateValues
     * @param currentPageValue
     * @return
     * @throws Exception
     */
    public PageValue updateGraph(int[] intUpdateValues, PageValue currentPageValue) throws Exception {
        // Merge the raw data at the right buffer position
        BufferCursorPositions bufferCursorPositions = getBufferCursorPositionsInRmsFrames(currentPageValue);
        int[] intCombinedValues;
        int intBufferPosition;
        if (intGraphRawValues == null) {
            // Brand new chart
            intGraphValuesMax = 0;
            intBufferPosition = 0;
        } else {
            // Already filled
            intBufferPosition = intGraphRawValues.length;
        }

        // Play position is not on same page as current buffer - its an error
        if (bufferCursorPositions.intPlayPosition == -1) {
            throw new Exception("Play cursor must be inside the displayed page");
        }


        // Re-use the old data where applicable
        if ((intBufferPosition == 0) && (bufferCursorPositions.intPlayPosition == 0)) {
            // Current play buffer and  actual buffer position is at zero, just add the new data
            intCombinedValues = new int[intUpdateValues.length];
        } else if (intBufferPosition < bufferCursorPositions.intPlayPosition) {
            // Current buffer is shorter than cursor start position, start with that and pad with zeros till play cursor position
            intCombinedValues = new int[bufferCursorPositions.intPlayPosition + intUpdateValues.length];
            // Use all of it
            if (intBufferPosition != 0) {
                System.arraycopy(intGraphRawValues, 0, intCombinedValues, 0, intGraphRawValues.length);
            }
            // Pad the missing ones with zeros
            Arrays.fill(intCombinedValues, intGraphRawValues.length, intCombinedValues.length-1,0);
        } else {
            // Current buffer is past play cursor start position
            // Shorten current buffer
            intCombinedValues = new int[bufferCursorPositions.intPlayPosition + intUpdateValues.length];
            System.arraycopy(intGraphRawValues, 0, intCombinedValues, 0, bufferCursorPositions.intPlayPosition);
        }
        // Add the new data
        System.arraycopy(intUpdateValues, 0, intCombinedValues, bufferCursorPositions.intPlayPosition, intUpdateValues.length);

        // Update cursors positions
        // Calculate new PageValue
        Log.d(TAG,"PlayPercentBefore=" + currentPageValue.fltPlayPercent);
        PageValue newPageValue = updatePageValueWhileRecording(currentPageValue, intUpdateValues.length);
        Log.d(TAG,"PlayPercentAfter=" + newPageValue.fltPlayPercent);
        BufferCursorPositions bufferNewCursorPositions = getBufferCursorPositionsInRmsFrames(newPageValue);

        // Manage paging, if an overflow into next page, just display the ones in the new page
        if (intCombinedValues.length > intPageSizeInRmsFrames) {
            // Overflow
            intGraphRawValues = new int[bufferNewCursorPositions.intPlayPosition];
            System.arraycopy(intCombinedValues, intCombinedValues.length - bufferNewCursorPositions.intPlayPosition , intGraphRawValues, 0, bufferNewCursorPositions.intPlayPosition);
        } else {
            // No overflow
            intGraphRawValues = intCombinedValues;
        }



        // Determine if post-end cursor data need to be added
        int[] intDisplayValues = intGraphRawValues;
        if (intAfterEndCursorRawValues != null) {
            if (bufferNewCursorPositions.intEndPosition != -1) {
                // Play cursor is on current displayed page, now to add the post-end cursor data
                if (!bufferNewCursorPositions.boolIsEndPositionAtAudioEnd) {
                    // Only need to add post-end if cursor not at the end
                    intDisplayValues = new int[intPageSizeInRmsFrames];
                    int intPostEndCursorDataAmount = intPageSizeInRmsFrames - bufferNewCursorPositions.intEndPosition;
                    System.arraycopy(intGraphRawValues, 0, intDisplayValues, 0, intGraphRawValues.length);
                    System.arraycopy(intAfterEndCursorRawValues, 0, intDisplayValues, bufferNewCursorPositions.intEndPosition, intPostEndCursorDataAmount);
                }
            }
        }


        // Scale data
        // Find amplitude of data
        for (int i = 0; i < intDisplayValues.length; i++) {
            if (intDisplayValues[i] > intGraphValuesMax) {
                intGraphValuesMax = intDisplayValues[i];
            }
        }

        // Normalise the complete raw graph
        int intGraphNormalizedBufSize = Math.min(intPageSizeInRmsFrames, intDisplayValues.length );
        fltGraphNormalizedValues = new float[intGraphNormalizedBufSize];

        for (int i = 0; i < intGraphNormalizedBufSize; i++) {
            fltGraphNormalizedValues[i] = (intDisplayValues[i] * 95.0f) / intGraphValuesMax;
        }
        // End of scaling



        return newPageValue;

    }

    public class BufferCursorPositions {
        public int intPlayPosition;
        public int intEndPosition;
        public boolean boolIsEndPositionAtAudioEnd;
    }

    /**
     * Determine from timeline cursor positions, where their positions are in the buffer
     * @param currentPageValue
     * @return
     */
    public BufferCursorPositions getBufferCursorPositionsInRmsFrames(PageValue currentPageValue) {
        BufferCursorPositions bufferPositions = new BufferCursorPositions();
        double fltPageWidth = intPageSizeInRmsFrames;
        int intPageAmount;
        if (currentPageValue.lngDataAmountInRmsFrames == 0) {
            intPageAmount = 1;
        } else {
            intPageAmount = (int) Math.ceil((double) currentPageValue.lngDataAmountInRmsFrames /intPageSizeInRmsFrames);
        }
        double fltTimeLineRangeInRmsFrames = intPageAmount * intPageSizeInRmsFrames;
        double fltPageHorStartPositionInTimeline = ((currentPageValue.fltPageNum - 1) * fltPageWidth);
        double fltPageHorEndPositionInTimeline = (currentPageValue.fltPageNum * fltPageWidth);
        double fltPlayPositionInTimeline = (currentPageValue.fltPlayPercent/100f) * fltTimeLineRangeInRmsFrames;
        double fltEndPositionInTimeline = (currentPageValue.fltEndPercent/100f) * fltTimeLineRangeInRmsFrames;

        if ((fltPlayPositionInTimeline >= fltPageHorStartPositionInTimeline) && (fltPlayPositionInTimeline <= fltPageHorEndPositionInTimeline)) {
            // Selected position is inside display page
            bufferPositions.intPlayPosition =  (int)(fltPlayPositionInTimeline - fltPageHorStartPositionInTimeline);
        } else if (fltPlayPositionInTimeline < fltPageHorStartPositionInTimeline){
            // Selected position is outside display page
            bufferPositions.intPlayPosition = -1;
        } else if (fltPlayPositionInTimeline > fltPageHorEndPositionInTimeline){
            // Selected position is outside display page
            bufferPositions.intPlayPosition = -1;
        }

        if ((fltEndPositionInTimeline >= fltPageHorStartPositionInTimeline) && (fltEndPositionInTimeline <= fltPageHorEndPositionInTimeline)) {
            // Selected position is inside display page
            bufferPositions.intEndPosition =  (int)(fltEndPositionInTimeline - fltPageHorStartPositionInTimeline);
        } else if (fltEndPositionInTimeline < fltPageHorStartPositionInTimeline){
            // Selected position is outside display page
            bufferPositions.intEndPosition = -1;
        } else if (fltEndPositionInTimeline > fltPageHorEndPositionInTimeline){
            // Selected position is outside display page
            bufferPositions.intEndPosition = -1;
        }

        if (Math.abs(fltEndPositionInTimeline - currentPageValue.lngDataAmountInRmsFrames) < 2) {
            bufferPositions.boolIsEndPositionAtAudioEnd = true;
        } else {
            bufferPositions.boolIsEndPositionAtAudioEnd = false;
        }
        return bufferPositions;
    }


    /**
     * Play cursor cannot be further right than real file length percentage
     * End cursor cannot be further right than real file length percentage
     * End cursor is set to real file length percentage if placed before the play cursor
     * The display starts with the play cursor inside the page
     *
     */
    public void updatePageValueBeforeRecording() {
        // Ensure the display starts with the play cursor inside the page
        cursorTimelinePage.setValue(cursorTimelinePlay.getValue());

        // Place cursors to make sense
        double fltDataAmountPercent = getDataAmountAsPercent();
        if (cursorTimelinePlay.getValue() > fltDataAmountPercent) {
            // Play cursor cannot be further right than real file length percentage
            cursorTimelinePlay.setValue(fltDataAmountPercent);
        }
        if (cursorTimelineEnd.getValue() > fltDataAmountPercent) {
            // End cursor cannot be further right than real file length percentage
            cursorTimelineEnd.setValue(fltDataAmountPercent);
        }
        if (cursorTimelineEnd.getValue() < cursorTimelinePlay.getValue()) {
            // End cursor is set to real file length percentage if placed before the play cursor
            cursorTimelineEnd.setValue(fltDataAmountPercent);
        }
    }

    /**
     * Change page value based on the amount of buffer added
     * @param currentPageValue
     * @param addedBufferLength
     * @return
     */
    private PageValue updatePageValueWhileRecording(PageValue currentPageValue, int addedBufferLength) {

        // Get present absolute positions
        double floatOldPageEndPosition = currentPageValue.fltPageNum * intPageSizeInRmsFrames;
        int intOldPageAmount = (int) Math.ceil((double) currentPageValue.lngDataAmountInRmsFrames /intPageSizeInRmsFrames);
        double floatOldPlayCursorTimelinePos = (currentPageValue.fltPlayPercent * intOldPageAmount * intPageSizeInRmsFrames)/100;
        double floatOldStartCursorTimelinePos = (currentPageValue.fltStartPercent * intOldPageAmount * intPageSizeInRmsFrames)/100;
        double floatOldEndCursorTimelinePos = (currentPageValue.fltEndPercent * intOldPageAmount * intPageSizeInRmsFrames)/100;

        // See what change after buffer addition
        double floatOldDiffBetweenEndAndPlayCursor =  floatOldPlayCursorTimelinePos - floatOldEndCursorTimelinePos;
        double floatNewDiffBetweenEndAndPlayCursor =  (floatOldPlayCursorTimelinePos + addedBufferLength) - floatOldEndCursorTimelinePos;

        long lngNewDataAmount = currentPageValue.lngDataAmountInRmsFrames;
        double floatNewPlayCursorTimelinePos = floatOldPlayCursorTimelinePos;
        double floatNewEndCursorTimelinePos = floatOldEndCursorTimelinePos;
        if ((floatNewDiffBetweenEndAndPlayCursor < 0) && (floatOldDiffBetweenEndAndPlayCursor < 0)) {
            // Play cursor has not caught up with end cursor yet, lngNewDataAmount remains high, as if all data is still there
            floatNewPlayCursorTimelinePos += addedBufferLength;
        } else if ((floatNewDiffBetweenEndAndPlayCursor >= 0) && (floatOldDiffBetweenEndAndPlayCursor < 0)) {
            // Play cursor has just now caught up with end cursor
            floatNewPlayCursorTimelinePos += addedBufferLength;
            lngNewDataAmount = (long) (currentPageValue.lngDataAmountInRmsFrames) + (addedBufferLength + (int)floatOldDiffBetweenEndAndPlayCursor);
            floatNewEndCursorTimelinePos = floatNewPlayCursorTimelinePos;
        } else if ((floatNewDiffBetweenEndAndPlayCursor < 0) && (floatOldDiffBetweenEndAndPlayCursor >= 0)) {
            // Can never happen, end cursor is always equal or later than play cursor
        } else {
            // Play cursor is running with end cursor for quite a while
            floatNewPlayCursorTimelinePos += addedBufferLength;
            lngNewDataAmount += addedBufferLength;
            floatNewEndCursorTimelinePos = floatNewPlayCursorTimelinePos;
        }


        // Recalculate the timeline percentages
        intPageAmount = (int) Math.ceil((double) lngNewDataAmount /intPageSizeInRmsFrames);
        double fltPlayPercent;
        double fltStartPercent;
        double fltEndPercent;
        double fltPageNum;
        if (floatNewPlayCursorTimelinePos > floatOldPageEndPosition) {
            // New page needs to be displayed
            fltPageNum = (double) Math.ceil(floatNewPlayCursorTimelinePos/intPageSizeInRmsFrames);
        } else {
            // Still on same page
            fltPageNum = currentPageValue.fltPageNum;
        }
        fltPlayPercent = (floatNewPlayCursorTimelinePos/(intPageAmount * intPageSizeInRmsFrames)) * 100;
        fltStartPercent = (floatOldStartCursorTimelinePos/(intPageAmount * intPageSizeInRmsFrames)) * 100;
        fltEndPercent = (floatNewEndCursorTimelinePos/(intPageAmount * intPageSizeInRmsFrames)) * 100;

        return new PageValue(fltPageNum, lngNewDataAmount, fltPlayPercent, fltStartPercent, fltEndPercent );
    }

    public PageValue updatePageValueWhilePlayback(PageValue currentPageValue, int newlyPlayedRmsFrameAmount) {

        // Get present absolute positions
        double floatOldPlayCursorTimelinePos = (currentPageValue.fltPlayPercent * intPageAmount * intPageSizeInRmsFrames)/100;
        double floatOldStartCursorTimelinePos = (currentPageValue.fltStartPercent * intPageAmount * intPageSizeInRmsFrames)/100;
        double floatOldEndCursorTimelinePos = (currentPageValue.fltEndPercent * intPageAmount * intPageSizeInRmsFrames)/100;
        double floatOldPageEndPosition = currentPageValue.fltPageNum * intPageSizeInRmsFrames;

        // See what change after frames addition
        double floatNewPlayCursorTimelinePos = floatOldPlayCursorTimelinePos + newlyPlayedRmsFrameAmount;

        // Recalculate the timeline percentages
        double fltNewPlayPercent = (floatNewPlayCursorTimelinePos/(intPageAmount * intPageSizeInRmsFrames)) * 100;
        double fltNewPageNum;
        if (floatNewPlayCursorTimelinePos > floatOldPageEndPosition) {
            // New page needs to be displayed
            fltNewPageNum = (double) Math.ceil(floatNewPlayCursorTimelinePos/intPageSizeInRmsFrames);
        } else {
            // Still on same page
            fltNewPageNum = currentPageValue.fltPageNum;
        }

        PageValue newPageValue = currentPageValue.copy();
        newPageValue.fltPlayPercent = fltNewPlayPercent;
        newPageValue.fltPageNum = fltNewPageNum;

        return newPageValue;
    }

    /**
     * Move all cursors to start cursor
     * Set to page which contains the start cursor
     * @param currentPageValue
     * @param lngDataAmountInRmsFrames
     * @return
     */
    public PageValue updatePageValueAfterDeletion(PageValue currentPageValue, long lngDataAmountInRmsFrames) {
        int intOldPageAmount;
        if (lngDataAmountInRmsFrames == 0) {
            intOldPageAmount = 1;
        } else {
            intOldPageAmount = (int) Math.ceil((double) currentPageValue.lngDataAmountInRmsFrames /intPageSizeInRmsFrames);
        }
        double floatOldStartCursorTimelinePos = (currentPageValue.fltStartPercent * intOldPageAmount * intPageSizeInRmsFrames)/100;

        double floatNewPlayCursorTimelinePos = floatOldStartCursorTimelinePos;
        double floatNewStartCursorTimelinePos = floatOldStartCursorTimelinePos;
        double floatNewEndCursorTimelinePos = floatOldStartCursorTimelinePos;

        int intNewPageAmount;
        if (lngDataAmountInRmsFrames == 0) {
            intNewPageAmount = 1;
        } else {
            intNewPageAmount = (int) Math.ceil((double) lngDataAmountInRmsFrames /intPageSizeInRmsFrames);
        }

        double fltNewPageNum = (double) Math.ceil(floatNewPlayCursorTimelinePos/intPageSizeInRmsFrames);

        double fltPlayPercent = (floatNewPlayCursorTimelinePos/(intNewPageAmount * intPageSizeInRmsFrames)) * 100;
        double fltStartPercent = (floatNewStartCursorTimelinePos/(intNewPageAmount * intPageSizeInRmsFrames)) * 100;
        double fltEndPercent = (floatNewEndCursorTimelinePos/(intNewPageAmount * intPageSizeInRmsFrames)) * 100;

        return new PageValue(fltNewPageNum, lngDataAmountInRmsFrames, fltPlayPercent, fltStartPercent, fltEndPercent );

    }




    /**
     * Calculate period based on a short pixels per sample
     * @return
     */


    public double getOptimalDataSampleBufferSizeInShortsAccurate(int intAudioSampleRate) {
        double fltPageSizeInSecs = intPageSizeInMs/1000f;
        double floatShortSamplesPerPage = intAudioSampleRate * fltPageSizeInSecs;
        double floatShortSamplesPerRmsValue = floatShortSamplesPerPage/rectGraph.width();
        return floatShortSamplesPerRmsValue;
    }



    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isInEditMode()) return;

        // Draw static items
        // Draw graph border
        canvas.drawRect(rectGraph, paintGraphFill);
        canvas.drawRect(rectGraph, paintGraphBorder);
        // Draw timeline
        canvas.drawLines(new float[] {pointTimelineStart.x, pointTimelineStart.y, pointTimelineEnd.x, pointTimelineEnd.y}, paintDefault);



        //Draw static positioned time values
        canvas.drawText("000.00", rectTimeStart.left, rectTimeStart.top , paintTime);
        canvas.drawText(pageValue.getFinalTimeString(), rectTimeEnd.left, rectTimeEnd.top, paintTime);
        // Draw time value lines
        canvas.drawLines(new float[] {pointTimelineStart.x, pointTimelineStart.y, pointTimelineStart.x, rectTimeStart.top - 2* intTimeHeight}, paintGraphBorder);
        canvas.drawLines(new float[] {pointTimelineEnd.x, pointTimelineStart.y, pointTimelineEnd.x, rectTimeEnd.top - 2* intTimeHeight}, paintGraphBorder);


        // Draw graph
        if (fltGraphNormalizedValues != null) {
            float intGraphHalfHeight = rectGraph.height()/2;
            for (int i = 0; i < fltGraphNormalizedValues.length - 1; i++) {
                floatGraphPoints[0] = rectGraph.left + getRmsFramePositionInPx(i);
                floatGraphPoints[1] = (rectGraph.top  + intGraphHalfHeight) - (fltGraphNormalizedValues[i]  * intGraphHalfHeight) / 100;
                floatGraphPoints[2] = rectGraph.left + getRmsFramePositionInPx(i);
                floatGraphPoints[3] = (rectGraph.top  + intGraphHalfHeight) + (fltGraphNormalizedValues[i]  * intGraphHalfHeight) / 100;

                floatGraphPoints[4] = rectGraph.left + getRmsFramePositionInPx(i + 1);
                floatGraphPoints[5] = (rectGraph.top  + intGraphHalfHeight) - (fltGraphNormalizedValues[i + 1]  * intGraphHalfHeight) / 100;
                floatGraphPoints[6] = rectGraph.left + getRmsFramePositionInPx(i + 1);
                floatGraphPoints[7] = (rectGraph.top  + intGraphHalfHeight) + (fltGraphNormalizedValues[i + 1]  * intGraphHalfHeight) / 100;

                canvas.drawLines(floatGraphPoints, paintWave);
            }
        }

        // Draw graph cursors
        cursorGraphStart.draw(canvas, true);
        cursorGraphEnd.draw(canvas, true);
        cursorGraphPlay.draw(canvas, true);

        // Draw timeline cursors
        cursorTimelinePage.draw(canvas, true);

//        // Draw graph/page connecting lines
//        canvas.drawLines(new double[] {rectGraph.left,rectGraph.bottom, cursorTimelinePage.pointLeftSide.x, cursorTimelinePage.pointLeftSide.y}, paintGraphBorder);
//        canvas.drawLines(new double[] {rectGraph.right,rectGraph.bottom, cursorTimelinePage.pointRightSide.x, cursorTimelinePage.pointRightSide.y}, paintGraphBorder);

        // Draw the rest
        cursorTimelinePlay.draw(canvas, true);
        cursorTimelineStart.draw(canvas, true);
        cursorTimelineEnd.draw(canvas, true);




    }



    float getRmsFramePositionInPx(int intRmsFrameNum) {
        float fltRmsFramePosInMs = (float) (intRmsFrameNum * fltRmsFramePeriodInMs);
        float fltRmsFramePosInPageInPx = (fltRmsFramePosInMs/intPageSizeInMs) * rectGraph.width();
        return fltRmsFramePosInPageInPx;
    }



    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // Width
        int width = widthSize;

        //Measure Height
        int height;
        if (heightMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = Math.min(intWidgetHeight, heightSize);
        } else {
            //Be whatever you want
            height = intWidgetHeight;
        }

        setMeasuredDimension(width, height);

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (isInEditMode()) return;

        // To prevent unnecessary triggering, only triggers when the width change
        if (w == oldw) return;

        // Set widget size
        // Account for padding
        double xpad = (double)(getPaddingLeft() + getPaddingRight());
        double ypad = (double)(getPaddingTop() + getPaddingBottom());


        double ww = (double)w - xpad;
        double hh = (double)h - ypad;
        intWidgetWidth = (int) ww;
        intWidgetHeight = (int) hh;

        // Announce initialisation complete
        if (boolIsInitializing) {

            initItemLocations();
            setPageValue(pageValue);

            // Trigger event to caller that initialization is done, to load the initial graph data
            boolIsInitializing = false;
            onInitCompleteListener.onComplete();
        } else {

            PageValue pageValueBeforeInit = pageValue.copy();
            initItemLocations();
            pageValue = pageValueBeforeInit;


            // Trigger event to caller that a size change took place, to reload the graph data for the new size
            // Convert PageValue to tie up with new display size
            double fltDataAmountInMs = pageValue.lngDataAmountInRmsFrames * fltRmsFramePeriodInMs;
            this.fltRmsFramePeriodInMs = ((double)this.intPageSizeInMs)/this.intPageSizeInRmsFrames;
            long lngDataAmountInRmsFramesNew = (long) (fltDataAmountInMs/fltRmsFramePeriodInMs);

            pageValue.lngDataAmountInRmsFrames = lngDataAmountInRmsFramesNew;

            // Trigger event to load new data
            onScreenSizeChangedListener.onChanged(pageValue);
        }

    }

    public static double pixelsToSp(Context context, double px) {
        double scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        return px/scaledDensity;
    }

    public static float spToPixels(Context context, float sp) {
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        return sp* scaledDensity;
    }

    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int px = Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return px;
    }

    public int pxToDp(int px) {
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int dp = Math.round(px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return dp;
    }

    public int getTextWidth(String text, Paint paint) {
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        int width = bounds.left + bounds.width();
        return width;
    }

    public int getTextHeight(String text, Paint paint) {
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        int height = bounds.bottom + bounds.height();
        return height;
    }


    public static enum HotSpotType {
        MIN, MAX, CENTER
    }

    public enum MotionState {
        DOWN, MOVING, UP
    }

    /**
     * @rectHotspot absolute rectangle representing hotspot
     * @rectHotspotValueRange absolute value from where value is determined
     */
    private abstract class Hotspot {
        public RectF rectHotspot;
        public RectF rectHotspotValueRange;
        public HotSpotType hotSpotType;
        public OnCursorChanged onCursorChanged;
        public boolean boolIsEnabled = true;


        public abstract void draw(Canvas canvas, boolean boolIsNormal);

        public boolean isInPressedRange(float touchX, float touchY) {
            if (boolIsEnabled) {
                return rectHotspot.contains(touchX, touchY );
            } else {
                return false;
            }
        }

        public double getValue(double touchX) {
            double result = ((touchX - rectHotspotValueRange.left) /(rectHotspotValueRange.right - rectHotspotValueRange.left)) * 100;
            return result;
        }

        public abstract void updateValue(double touchX, MotionState motionState);
    }

    private abstract class Cursor extends Hotspot {
        private Bitmap bmpImageNormal;
        private Bitmap bmpImagePressed;
        private Bitmap bmpImageHidden;
        protected RectF rectDefault;
        protected int intImageHeight;
        protected int intImageWidth;
        protected int intImageHalfHeight;
        protected int intImageHalfWidth;
        protected double fltValue;
        protected double fltGraphHeight = rectGraph.height();
        protected double fltTimeLineHeight = intThumbHeight * 2;

        protected int intValueTimelineRange = (int)(pointTimelineEnd.x - pointTimelineStart.x);

        public Paint paintCursorLine;
        protected  final int intCursorLineSizeInDp = 3;
        protected  int intCursorLineColor;

        boolean boolIsVisible;

        public Cursor(Bitmap bmpImageNormal, Bitmap bmpImagePressed, HotSpotType hotSpotType, OnCursorChanged onCursorChanged ) {
            if (bmpImageNormal != null) {
                this.bmpImageNormal = bmpImageNormal;
                this.bmpImagePressed = bmpImagePressed;
                this.bmpImageHidden = bmpCursorHidden;
                rectDefault = new RectF(0, 0, bmpImageNormal.getWidth(), bmpImageNormal.getHeight());
                intImageHeight = (int) rectDefault.height();
                intImageHalfHeight = (int) (rectDefault.height() / 2);
                intImageWidth = (int) rectDefault.width();
                intImageHalfWidth = (int) (rectDefault.width() / 2);
                rectHotspot = new RectF(rectDefault);
                this.hotSpotType = hotSpotType;
                this.onCursorChanged = onCursorChanged;

                paintCursorLine = new Paint();
                paintCursorLine.setStrokeWidth(dpToPx(intCursorLineSizeInDp));
                paintCursorLine.setAntiAlias(true);
                paintCursorLine.setStyle(Paint.Style.FILL);

                this.boolIsVisible = true;
            }
        }

        /**
         * Update pointCenter and rectHotspot based on fltValue
         * @param fltValue 0 -> 100%
         */
        public abstract void setValue(double fltValue);

        public double getValue() {
            return fltValue;
        }

        public void setVisibility(boolean boolIsVisible) {
            this.boolIsVisible = boolIsVisible;
        }

        @Override
        public void draw(Canvas canvas, boolean boolIsNormal) {
            Bitmap bitMap;
            if (! boolIsVisible || ! boolIsEnabled)  {
                bitMap = bmpCursorHidden;
            } else {
                if (boolIsNormal) {
                    bitMap = bmpImageNormal;
                } else {
                    bitMap = bmpImagePressed;
                }
            }
            canvas.drawBitmap(bitMap, rectHotspot.left, rectHotspot.top, null );
        }

        @Override
        public void updateValue(double touchX, MotionState motionState) {
            touchX = Math.max(rectHotspotValueRange.left, touchX);
            touchX = Math.min(rectHotspotValueRange.right, touchX);
            fltValue = super.getValue(touchX);
        }

        public double convertFromTimeLineValue(double fltTimeLineValue) {
            double fltPageWidth = (double)intValueTimelineRange / intPageAmount;
            double fltPageHorStartPosition = ((pageValue.fltPageNum - 1) * fltPageWidth);
            double fltPageHorEndPosition = (pageValue.fltPageNum * fltPageWidth);
            double fltTimeLinePosition = (fltTimeLineValue /100f) *intValueTimelineRange;

            if ((fltTimeLinePosition >= fltPageHorStartPosition) && (fltTimeLinePosition <= fltPageHorEndPosition)) {
                // Selected position is inside display page
                double fltMyValue = ((fltTimeLinePosition - fltPageHorStartPosition)/fltPageWidth) * 100;
                return fltMyValue;
            } else if (fltTimeLinePosition < fltPageHorStartPosition){
                // Selected position is outside display page
                return 0.0f;
            } else if (fltTimeLinePosition > fltPageHorEndPosition){
                // Selected position is outside display page
                return 100.0f;
            }
            return 0;
        }

    }

    private class CursorGraphPlay extends Cursor {

        private int intValueGraphRange;
        private int intValueTimelineRange = (int)(pointTimelineEnd.x - pointTimelineStart.x);


        private CursorGraphPlay(Bitmap bmpImageNormal, Bitmap bmpImagePressed, HotSpotType hotSpotType, OnCursorChanged onCursorChanged) {
            super(bmpImageNormal, bmpImagePressed, hotSpotType, onCursorChanged);
            intValueGraphRange = (int)(rectGraph.right - rectGraph.left);
            rectHotspotValueRange = new RectF(rectGraph.left, 0, rectGraph.right, 0);
            intCursorLineColor = Color.parseColor("#2fa83f");
            paintCursorLine.setColor(intCursorLineColor);
        }

        @Override
        public void setValue(double fltValue) {
            float intValueX = (float)(rectGraph.left + ((fltValue /100.0) * intValueGraphRange));
            rectHotspot.offsetTo(intValueX - intImageHalfWidth, rectGraph.top - intImageHalfHeight );
            this.fltValue = fltValue;
        }

        /**
         * This event gets triggered when the user moves the cursor
         * @param touchX
         * @param motionState
         */
        @Override
        public void updateValue(double touchX, MotionState motionState) {
            super.updateValue(touchX, motionState);
            double fltValue = getValue();
            // Determine if file length exceeded
            double fltPlayCursorTimelineValue = cursorTimelinePage.getTimelineValue(fltValue);
            double fltDataAmountAsPercent = getDataAmountAsPercent();
            if (fltPlayCursorTimelineValue > fltDataAmountAsPercent) {
                // Exceeded, clip the position
                fltValue = getValueAsPercentInPage(fltDataAmountAsPercent);
            }

            setValue(fltValue);
            setVisibility(true);

            // Update slave hotspot
            cursorTimelinePlay.setToCursorGraphValue(fltValue);
        }



        @Override
        public void draw(Canvas canvas, boolean boolIsNormal) {

            // Draw cursor line
            if (fltValue > 0 && fltValue < 100) {
                canvas.drawLine(rectHotspot.centerX(),
                        rectHotspot.centerY(),
                        rectHotspot.centerX(),
                        (float) (rectHotspot.centerY() + fltGraphHeight), paintCursorLine);
            }

            super.draw(canvas, boolIsNormal);
        }

        public void setToCursorTimelineValue(double fltTimeLineValue) {
            double fltPageWidth = (double) intValueTimelineRange / intPageAmount;
            double fltPageHorStartPosition = ((pageValue.fltPageNum - 1) * fltPageWidth);
            double fltPageHorEndPosition = (pageValue.fltPageNum * fltPageWidth);
            double fltTimeLinePosition = (fltTimeLineValue /100f) *intValueTimelineRange;

            if ((fltTimeLinePosition >= fltPageHorStartPosition) && (fltTimeLinePosition <= fltPageHorEndPosition)) {
                // Selected position is inside display page
                double fltMyValue = ((fltTimeLinePosition - fltPageHorStartPosition)/fltPageWidth) * 100;
                setValue(fltMyValue);
                setVisibility(true);
            } else if (fltTimeLinePosition < fltPageHorStartPosition){
                // Selected position is outside display page
                setValue(0);
                setVisibility(false);
            } else if (fltTimeLinePosition > fltPageHorEndPosition){
                // Selected position is outside display page
                setValue(100);
                setVisibility(false);
            }
        }
    }

    private class CursorGraphStart extends Cursor {

        private int intValueTimelineRange = (int)(pointTimelineEnd.x - pointTimelineStart.x);

        private CursorGraphStart(Bitmap bmpImageNormal, Bitmap bmpImagePressed, HotSpotType hotSpotType, OnCursorChanged onCursorChanged) {
            super(bmpImageNormal, bmpImagePressed, hotSpotType, onCursorChanged);
            rectHotspotValueRange = new RectF(rectGraph.left, 0, rectGraph.right, 0);
            intCursorLineColor = Color.parseColor("#e0bc1d");
            paintCursorLine.setColor(intCursorLineColor);
        }

        @Override
        public void setValue(double fltValue) {

            double fltEndValue =   convertFromTimeLineValue(pageValue.fltEndPercent);
            fltValue = Math.min(fltEndValue, fltValue);

            float intValueX = (float)(rectGraph.left + ((fltValue /100.0f) * (rectGraph.right - rectGraph.left)));
            rectHotspot.offsetTo(intValueX - intImageHalfWidth, rectGraph.bottom - intImageHalfHeight );
            this.fltValue = fltValue;
        }

        @Override
        public void updateValue(double touchX, MotionState motionState) {
            super.updateValue(touchX, motionState);
            setValue(getValue());
            setVisibility(true);

            // Update slave hotspot
            cursorTimelineStart.setToCursorGraphValue(fltValue);
        }

        @Override
        public void draw(Canvas canvas, boolean boolIsNormal) {

            // Draw cursor line
            if (fltValue > 0 && fltValue < 100) {
                canvas.drawLine(rectHotspot.centerX(),
                        rectHotspot.centerY(),
                        rectHotspot.centerX(),
                        (float) (rectHotspot.centerY() - fltGraphHeight), paintCursorLine);
            }

            super.draw(canvas, boolIsNormal);
        }

        public void setToCursorTimelineValue(double fltTimeLineValue) {
            double fltPageWidth = (double) intValueTimelineRange / intPageAmount;
            double fltPageHorStartPosition = ((pageValue.fltPageNum - 1) * fltPageWidth);
            double fltPageHorEndPosition = (pageValue.fltPageNum * fltPageWidth);
            double fltTimeLinePosition = (fltTimeLineValue /100f) *intValueTimelineRange;

            if ((fltTimeLinePosition >= fltPageHorStartPosition) && (fltTimeLinePosition <= fltPageHorEndPosition)) {
                // Selected position is inside display page
                double fltMyValue = ((fltTimeLinePosition - fltPageHorStartPosition)/fltPageWidth) * 100;
                setValue(fltMyValue);
                setVisibility(true);
            } else if (fltTimeLinePosition < fltPageHorStartPosition){
                // Selected position is outside display page
                setValue(0);
                setVisibility(false);
            } else if (fltTimeLinePosition > fltPageHorEndPosition){
                // Selected position is outside display page
                setValue(100);
                setVisibility(false);
            }
        }

    }

    private class CursorGraphEnd extends Cursor {



        private CursorGraphEnd(Bitmap bmpImageNormal, Bitmap bmpImagePressed, HotSpotType hotSpotType, OnCursorChanged onCursorChanged) {
            super(bmpImageNormal, bmpImagePressed, hotSpotType, onCursorChanged);
            rectHotspotValueRange = new RectF(rectGraph.left, 0, rectGraph.right, 0);
            intCursorLineColor = Color.parseColor("#e0bc1d");
            paintCursorLine.setColor(intCursorLineColor);
        }

        @Override
        public void setValue(double fltValue) {

            double fltStartValue =   convertFromTimeLineValue(pageValue.fltStartPercent);
            fltValue = Math.max(fltStartValue, fltValue);

            float intValueX = (float)(rectGraph.left + ((fltValue /100.0f) * (rectGraph.right - rectGraph.left)));
            rectHotspot.offsetTo(intValueX - intImageHalfWidth, rectGraph.bottom - intImageHalfHeight );
            this.fltValue = fltValue;
        }

        @Override
        public void updateValue(double touchX, MotionState motionState) {
            super.updateValue(touchX, motionState);
            setValue(getValue());
            setVisibility(true);

            // Update slave hotspot
            cursorTimelineEnd.setToCursorGraphValue(fltValue);


        }

        @Override
        public void draw(Canvas canvas, boolean boolIsNormal) {

            // Draw cursor line
            if (fltValue > 0 && fltValue < 100) {
                canvas.drawLine(rectHotspot.centerX(),
                        rectHotspot.centerY(),
                        rectHotspot.centerX(),
                        (float) (rectHotspot.centerY() - fltGraphHeight), paintCursorLine);
            }

            super.draw(canvas, boolIsNormal);
        }

        public void setToCursorTimelineValue(double fltTimeLineValue) {
            double fltPageWidth = (double)intValueTimelineRange / intPageAmount;
            double fltPageHorStartPosition = ((pageValue.fltPageNum - 1) * fltPageWidth);
            double fltPageHorEndPosition = (pageValue.fltPageNum * fltPageWidth);
            double fltTimeLinePosition = (fltTimeLineValue /100f) *intValueTimelineRange;

            if ((fltTimeLinePosition >= fltPageHorStartPosition) && (fltTimeLinePosition <= fltPageHorEndPosition)) {
                // Selected position is inside display page
                double fltMyValue = ((fltTimeLinePosition - fltPageHorStartPosition)/fltPageWidth) * 100;
                setValue(fltMyValue);
                setVisibility(true);
            } else if (fltTimeLinePosition < fltPageHorStartPosition){
                // Selected position is outside display page
                setValue(0);
                setVisibility(false);
            } else if (fltTimeLinePosition > fltPageHorEndPosition){
                // Selected position is outside display page
                setValue(100);
                setVisibility(false);
            }
        }



    }

    private class CursorTimelinePlay extends Cursor {

        int intValueRange;
        int intCenterVertOffset;

        private CursorTimelinePlay(Bitmap bmpImageNormal, Bitmap bmpImagePressed, HotSpotType hotSpotType, OnCursorChanged onCursorChanged) {
            super(bmpImageNormal, bmpImagePressed, hotSpotType, onCursorChanged);
            intValueRange = (int)(pointTimelineEnd.x - pointTimelineStart.x);
            intCenterVertOffset = (int)(rectGraph.bottom + intThumbHeight + intTimeHeight);
            rectHotspotValueRange = new RectF(pointTimelineStart.x, 0, pointTimelineEnd.x, 0);
            intCursorLineColor = Color.parseColor("#2fa83f");
            paintCursorLine.setColor(intCursorLineColor);
        }

        @Override
        public void setValue(double fltValue) {
            this.fltValue = fltValue;
            pageValue.fltPlayPercent = fltValue;

            float intValueX = (float)(pointTimelineStart.x + ((fltValue /100.0f) * intValueRange));
            rectHotspot.offsetTo(intValueX - intImageHalfWidth, intCenterVertOffset - intImageHalfHeight );

        }

        @Override
        public void updateValue(double touchX, MotionState motionState) {
            super.updateValue(touchX, motionState);

            // Determine if file length exceeded
            double fltDataAmountAsPercent = getDataAmountAsPercent();
            if (fltValue > fltDataAmountAsPercent) {
                // Exceeded, clip the position
                fltValue = getValueAsPercentInPage(fltDataAmountAsPercent);
            }
            setValue(fltValue);

            // Set slave cursor value
            cursorGraphPlay.setToCursorTimelineValue(fltValue);
        }

        @Override
        public void draw(Canvas canvas, boolean boolIsNormal) {
            if (!this.boolIsVisible) return;
            // Draw cursor line
            canvas.drawLine(rectHotspot.centerX(),
                    rectHotspot.centerY(),
                    rectHotspot.centerX(),
                    rectHotspot.centerY() + intThumbHeight, paintCursorLine);

            super.draw(canvas, boolIsNormal);

            // Draw dynamic positioned time values
            rectTimePlay.offsetTo(rectHotspot.centerX() - intTimeHalfWidth, rectHotspot.centerY() - intThumbHalfHeight);


            // Draw
            canvas.drawText(pageValue.getPlayTimeString(), rectTimePlay.left, rectTimePlay.top, paintTime);
        }

        public void setToCursorGraphValue(double fltValue) {
            double fltPageWidth = (double) intValueRange / intPageAmount;
            double fltPageHorPosition = ((pageValue.fltPageNum - 1) * fltPageWidth) + ((fltValue/100.0f) * fltPageWidth);
            double fltOwnValue = (fltPageHorPosition/intValueRange) * 100f;
            setValue(fltOwnValue);
        }



    }

    private class CursorTimelineStart extends Cursor {

        int intValueRange;
        int intCenterVertOffset;

        private CursorTimelineStart(Bitmap bmpImageNormal, Bitmap bmpImagePressed, HotSpotType hotSpotType, OnCursorChanged onCursorChanged) {
            super(bmpImageNormal, bmpImagePressed, hotSpotType, onCursorChanged);
            intValueRange = (int)(pointTimelineEnd.x - pointTimelineStart.x);
            rectHotspotValueRange = new RectF(pointTimelineStart.x, 0, pointTimelineEnd.x, 0);
            intCenterVertOffset = (int)(rectGraph.bottom + (3 * intThumbHeight) + intTimeHeight);
            rectHotspotValueRange = new RectF(pointTimelineStart.x, 0, pointTimelineEnd.x, 0);
            intCursorLineColor = Color.parseColor("#e0bc1d");
            paintCursorLine.setColor(intCursorLineColor);
        }

        @Override
        public void setValue(double fltValue) {

            double fltEndValue = pageValue.fltEndPercent;
            fltValue = Math.min(fltEndValue, fltValue);
            this.fltValue = fltValue;
            pageValue.fltStartPercent = fltValue;

            float fltValueX = (float)(pointTimelineStart.x + ((fltValue /100.0f) * intValueRange));
            rectHotspot.offsetTo(fltValueX - intImageHalfWidth, intCenterVertOffset - intImageHalfHeight);

        }

        @Override
        public void updateValue(double touchX, MotionState motionState) {
            super.updateValue(touchX, motionState);
            setValue(getValue());

            // Set slave cursor value
            cursorGraphStart.setToCursorTimelineValue(fltValue);
        }

        @Override
        public void draw(Canvas canvas, boolean boolIsNormal) {
            if (!this.boolIsVisible) return;
            // Draw cursor line
            canvas.drawLine(rectHotspot.centerX(),
                    rectHotspot.centerY(),
                    rectHotspot.centerX(),
                    rectHotspot.centerY() - intThumbHeight, paintCursorLine);

            super.draw(canvas, boolIsNormal);
        }

        public void setToCursorGraphValue(double intValue) {
            double fltPageWidth = (double)intValueRange / intPageAmount;
            double fltPageHorPosition = ((pageValue.fltPageNum - 1) * fltPageWidth) + ((intValue/100.0f) * fltPageWidth);
            double fltOwnValue = (fltPageHorPosition/intValueRange) * 100f;
            setValue(fltOwnValue);
        }

    }

    private class CursorTimelineEnd extends Cursor {

        int intValueRange;
        int intCenterVertOffset;

        private CursorTimelineEnd(Bitmap bmpImageNormal, Bitmap bmpImagePressed, HotSpotType hotSpotType, OnCursorChanged onCursorChanged) {
            super(bmpImageNormal, bmpImagePressed, hotSpotType, onCursorChanged);
            intValueRange = (int)(pointTimelineEnd.x - pointTimelineStart.x);
            intCenterVertOffset = (int)(rectGraph.bottom + (3 * intThumbHeight));
            rectHotspotValueRange = new RectF(pointTimelineStart.x, 0, pointTimelineEnd.x, 0);
            intCenterVertOffset = (int)(rectGraph.bottom + (3 * intThumbHeight) + intTimeHeight);
            rectHotspotValueRange = new RectF(pointTimelineStart.x, 0, pointTimelineEnd.x, 0);
            intCursorLineColor = Color.parseColor("#e0bc1d");
            paintCursorLine.setColor(intCursorLineColor);
        }

        @Override
        public void setValue(double fltValue) {

            double fltStartValue = pageValue.fltStartPercent;
            fltValue = Math.max(fltStartValue, fltValue);
            this.fltValue = fltValue;
            pageValue.fltEndPercent = fltValue;

            float fltValueX = (float)(pointTimelineStart.x + ((fltValue /100.0f) * intValueRange));
            rectHotspot.offsetTo(fltValueX - intImageHalfWidth, intCenterVertOffset - intImageHalfHeight );


        }

        @Override
        public void updateValue(double touchX, MotionState motionState) {
            super.updateValue(touchX, motionState);
            double fltEndCursorPercent = getValue();
            setValue(fltEndCursorPercent);
            pageValue.fltEndPercent = fltEndCursorPercent;

            // Set slave cursor value
            cursorGraphEnd.setToCursorTimelineValue(fltEndCursorPercent);

            // Trigger event if registered, to save a page length of data following the end cursor
            if (onEndCursorChangedListener != null) {
                double fltDataAmountAsPercent = getDataAmountAsPercent();
                if (this.fltValue < fltDataAmountAsPercent) {
                    // Only fire event of end cursor NOT at end of data
                    intAfterEndCursorRawValues = onEndCursorChangedListener.getSinglePageAfterCursorBuffer(this.fltValue);
                } else {
                    intAfterEndCursorRawValues = null;
                }
            }

        }

        @Override
        public void draw(Canvas canvas, boolean boolIsNormal) {
            if (!this.boolIsVisible) return;
            // Draw cursor line
            canvas.drawLine(rectHotspot.centerX(),
                    rectHotspot.centerY(),
                    rectHotspot.centerX(),
                    rectHotspot.centerY() - intThumbHeight, paintCursorLine);

            super.draw(canvas, boolIsNormal);
        }

        public void setToCursorGraphValue(double fltValue) {
            double intPageWidth = (double)intValueRange / intPageAmount;
            double fltPageHorPosition = ((pageValue.fltPageNum - 1.0f) * intPageWidth) + ((fltValue/100.0f) * intPageWidth);
            double fltOwnValue = (fltPageHorPosition/intValueRange) * 100f;
            setValue(fltOwnValue);

            // Trigger event if registered, to save a page length of data following the end cursor
            if (onEndCursorChangedListener != null) {
                double fltDataAmountAsPercent = getDataAmountAsPercent();
                if (fltOwnValue < fltDataAmountAsPercent) {
                    // Only fire event of end cursor NOT at end of data
                    intAfterEndCursorRawValues = onEndCursorChangedListener.getSinglePageAfterCursorBuffer(fltOwnValue);
                } else {
                    intAfterEndCursorRawValues = null;
                }
            }
        }

    }

    private class CursorTimelinePage extends Cursor {

        private final int intRadiusInDp = 5;
        private final int intCursorHeight = intThumbHalfHeight;
        private int intCursorHalfHeight = intCursorHeight/2;
        private int intCursorHalfWidth = intThumbHalfWidth/2;
        private int intCursorQuarterHeight = intCursorHeight/4;
        private int intCenterVertOffset;

        private int intValueRange = (int)(pointTimelineEnd.x - pointTimelineStart.x);
        private Paint paintPageCursor;
        private int intRadiusInPx;
        private int intPageColor = Color.parseColor(PAGE_COLOR);

        public PointF pointLeftSide;
        public PointF pointRightSide;

        public CursorTimelinePage(Bitmap bmpImageNormal, Bitmap bmpImagePressed, HotSpotType hotSpotType, OnCursorChanged onCursorChanged) {
            super(bmpImageNormal, bmpImagePressed, hotSpotType, onCursorChanged);
            paintPageCursor = new Paint();
            paintPageCursor.setStrokeWidth(3f);
            paintPageCursor.setAntiAlias(true);
            paintPageCursor.setColor(intPageColor);
            paintPageCursor.setStyle(Paint.Style.FILL);
            intRadiusInPx = dpToPx(intRadiusInDp);
            intCenterVertOffset = (int)(rectGraph.bottom + (2 * intThumbHeight) + intTimeHeight);
            rectHotspotValueRange = new RectF(pointTimelineStart.x, 0, pointTimelineEnd.x, 0);
            this.onCursorChanged = onCursorChanged;
        }

        /**
         *
         * @param pageNewValue .intPageNum = 1.0 -> intPages (1 is leftmost, intPages is rightmost
         * @param pageNewValue  .intPageAmount 1 -> infinity
         */
        public void setValue(PageValue pageNewValue) {
            double fltIndexIntoPages;
            fltIndexIntoPages = Math.max(1, pageNewValue.fltPageNum);
            fltIndexIntoPages = Math.min(intPageAmount, fltIndexIntoPages);

            if (pageValue.lngDataAmountInRmsFrames == 0) {
                intPageAmount = 1;
            } else {
                intPageAmount = (int) Math.ceil((double) pageValue.lngDataAmountInRmsFrames /intPageSizeInRmsFrames);
            }
            double fltPageWidth = (double)intValueRange / intPageAmount;
            double fltPageHorPosition = (fltIndexIntoPages * fltPageWidth) - (fltPageWidth/2.0f);
            double fltValue = (fltPageHorPosition/intValueRange) * 100.0f;
            setValue(fltValue);
            Log.d("TAG", "fltValue 3 = " + this.fltValue);
            pageValue = pageNewValue;
            pageValue.fltPageNum = fltIndexIntoPages;

            // Set other cursor on timeline
            cursorTimelinePlay.setValue(pageNewValue.fltPlayPercent);
            cursorTimelineStart.setValue(pageNewValue.fltStartPercent);
            cursorTimelineEnd.setValue(pageNewValue.fltEndPercent);

            // Set slave cursor value
            cursorGraphPlay.setToCursorTimelineValue(pageNewValue.fltPlayPercent);
            cursorGraphStart.setToCursorTimelineValue(pageNewValue.fltStartPercent);
            cursorGraphEnd.setToCursorTimelineValue(pageNewValue.fltEndPercent);

        }

        /**
         *
         * @param fltValueNew 0 -> 100%
         */
        @Override
        public void setValue(double fltValueNew) {
            float fltValueX = (float)(pointTimelineStart.x + ((fltValueNew /100.0f) * intValueRange));
            rectHotspot.offsetTo(fltValueX - intImageHalfWidth, intCenterVertOffset - intImageHalfHeight );
            this.fltValue = fltValueNew;
            Log.d("TAG", "fltValue 1 = " + this.fltValue);
            Log.d("TAG", "rectHotspot.centerX() = " + rectHotspot.centerX());
        }

        @Override
        public void updateValue(double touchX, MotionState motionState) {
            // Determine new position inside range
            double fltPageWidth = intValueRange / intPageAmount;
            touchX = Math.max(rectHotspotValueRange.left + fltPageWidth/2.0f, touchX);
            touchX = Math.min(rectHotspotValueRange.right - fltPageWidth/2.0f, touchX);
            super.updateValue(touchX, motionState); // Get percent offset into range
            Log.d("TAG", "fltValue 2 = " + fltValue);
            Log.d("TAG", "rectHotspot.centerX() = " + rectHotspot.centerX());
            setValue(fltValue); // Change the display


            // Determine the new page number
            double fltPageHorPosition = (fltValue /100f) * intValueRange;
            pageValue.fltPageNum = (fltPageHorPosition + fltPageWidth/2.0f)/fltPageWidth;

            // Set slave cursor value
            cursorGraphPlay.setToCursorTimelineValue(cursorTimelinePlay.getValue());
            cursorGraphStart.setToCursorTimelineValue(cursorTimelineStart.getValue());
            cursorGraphEnd.setToCursorTimelineValue(cursorTimelineEnd.getValue());

            if (onCursorChanged != null) {
                onCursorChanged.OnValueChange(fltValue);
            }
        }

        @Override
        public void draw(Canvas canvas, boolean boolIsNormal) {
            float fltPageWidth = (float)intValueRange / intPageAmount;
            float fltPageHalfWidth = fltPageWidth/2;

            // Draw page rect
            RectF rectPage = new RectF(0,0, fltPageHalfWidth, intCursorHalfHeight );
            // Draw left side
            pointLeftSide = new PointF(rectHotspot.centerX() - fltPageHalfWidth, rectHotspot.centerY());
            pointRightSide = new PointF(pointLeftSide.x + fltPageWidth, rectHotspot.centerY());
            rectPage.offsetTo(rectHotspot.centerX() - fltPageHalfWidth, rectHotspot.centerY() - intCursorQuarterHeight);
            // canvas.drawRoundRect(rectPage,intRadiusInPx, intRadiusInPx, paintPageCursor);
            canvas.drawRect(rectPage, paintPageCursor);
            // Draw right side
            rectPage.offsetTo(rectHotspot.centerX(), rectHotspot.centerY() - intCursorQuarterHeight);
            // canvas.drawRoundRect(rectPage,intRadiusInPx, intRadiusInPx, paintPageCursor);
            canvas.drawRect(rectPage, paintPageCursor);

            // Draw hotspot
            super.draw(canvas, boolIsNormal);
            Log.d("TAG", "fltValue 4 = " + fltValue);
            Log.d("TAG", "rectHotspot.centerX() = " + rectHotspot.centerX());
        }

        public double getTimelineValue(double fltValueAsPercentInsidePage) {
            double fltPageWidth = (double)intValueRange / intPageAmount;

            double fltPageHorPosition = ((pageValue.fltPageNum - 1) * fltPageWidth) + ((fltValueAsPercentInsidePage/100.0f) * fltPageWidth);
            return ((fltPageHorPosition/intValueRange) * 100f);
        }


    }





    /**
     * Handles thumb selection and movement. Notifies listener callback on
     * certain events.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (!isEnabled())
            return false;

        int pointerIndex;

        final int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN:
                // Remember where the motion event started
                mActivePointerId = event.getPointerId(event.getPointerCount() - 1);
                pointerIndex = event.findPointerIndex(mActivePointerId);
                mDownMotionX = event.getX(pointerIndex);
                mDownMotionY = event.getY(pointerIndex);

                hotspotPressed = evalPressedHotspot(mDownMotionX, mDownMotionY);

                // Only handle hotspot presses.
                if (hotspotPressed == null)
                    return super.onTouchEvent(event);

                setPressed(true);
                onStartTrackingTouch();
                trackTouchEvent(event, MotionState.DOWN);
                attemptClaimDrag();
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (hotspotPressed != null) {
                    if (mIsDragging) {
                        trackTouchEvent(event, MotionState.MOVING);
                    } else {
                        // Scroll to follow the motion event
                        pointerIndex = event.findPointerIndex(mActivePointerId);
                        final double x = event.getX(pointerIndex);

                        if (Math.abs(x - mDownMotionX) > mScaledTouchSlop) {
                            setPressed(true);
                            onStartTrackingTouch();
                            trackTouchEvent(event, MotionState.MOVING);
                            attemptClaimDrag();
                        }
                    }
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsDragging) {
                    trackTouchEvent(event, MotionState.UP);
                    onStopTrackingTouch();
                    setPressed(false);
                } else {
                    // Touch up when we never crossed the touch slop threshold
                    // should be interpreted as a tap-seek to that location.
                    onStartTrackingTouch();
                    trackTouchEvent(event, MotionState.UP);
                    onStopTrackingTouch();
                }

                invalidate();
                hotspotPressed = null;
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = event.getPointerCount() - 1;
                // final int index = ev.getActionIndex();
                mDownMotionX = event.getX(index);
                mActivePointerId = event.getPointerId(index);
                invalidate();
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                invalidate();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsDragging) {
                    onStopTrackingTouch();
                    setPressed(false);
                }
                invalidate(); // see above explanation
                break;
        }
        return true;
    }

    private Hotspot evalPressedHotspot(float touchX, float touchY) {
        Hotspot result = null;
        ArrayList<Hotspot> hotSpotsPressed = new ArrayList<Hotspot>();
        for (Hotspot hotSpot: hotSpots) {
            if (hotSpot.isInPressedRange(touchX, touchY)) {
                hotSpotsPressed.add(hotSpot);
            }
        }

        if (hotSpotsPressed.size() == 1) {
            result = hotSpotsPressed.get(0);
        } else if (hotSpotsPressed.size() == 2) {
            // If both hotspots are pressed (they lie on top of each other),
            // choose the one with more room to drag. this avoids "stalling" the
            // hotspots in a corner, not being able to drag them apart anymore.
            if ((hotSpotsPressed.get(0).hotSpotType != HotSpotType.CENTER) &&
                    (hotSpotsPressed.get(1).hotSpotType != HotSpotType.CENTER)){
                HotSpotType hotSpotType = (touchX / getWidth() > 0.5f) ? HotSpotType.MIN : HotSpotType.MAX;
                for (Hotspot hotSpot: hotSpotsPressed) {
                    if (hotSpot.hotSpotType == hotSpotType) {
                        result = hotSpot;
                    }
                }
            }
        }
        return result;
    }

    /**
     * This is called when the user has started touching this widget.
     */
    void onStartTrackingTouch() {
        mIsDragging = true;
    }

    /**
     * This is called when the user either releases his touch or the touch is
     * canceled.
     */
    void onStopTrackingTouch() {
        mIsDragging = false;
    }

    /**
     * Tries to claim the user's drag motion, and requests disallowing any
     * ancestors from stealing events in the drag.
     */
    private void attemptClaimDrag() {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }


    private final void trackTouchEvent(MotionEvent event, MotionState motionState) {
        final int pointerIndex = event.findPointerIndex(mActivePointerId);
        final double x = event.getX(pointerIndex);

        hotspotPressed.updateValue(x, motionState);
        invalidate();
    }

    private final void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;

        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose
            // a new active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mDownMotionX = ev.getX(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }
}
