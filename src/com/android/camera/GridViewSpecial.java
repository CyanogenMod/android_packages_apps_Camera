package com.android.camera;

import com.android.camera.gallery.IImage;
import com.android.camera.gallery.IImageList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.Scroller;

class GridViewSpecial extends View {
    public static final int ORIGINAL_SELECT = -2;
    public static final int REMOVE_SELCTION = -1;

    private static final String TAG = "GridViewSpecial";
    private IImageList mAllImages = ImageManager.emptyImageList();
    private final Paint mPatin = new Paint();

    ImageBlockManager mImageBlockManager;
    private Handler mHandler;

    private LayoutSpec mCurrentSpec;
    boolean mShowSelection = false;
    int mCurrentSelection = -1;
    private boolean mCurrentSelectionPressed;

    private Listener mListener = null;

    long mVideoSizeLimit;
    private boolean mRunning = false;

    class LayoutSpec {
        LayoutSpec(int cols, int w, int h, int leftEdgePadding,
                   int rightEdgePadding, int intercellSpacing) {
            mColumns = cols;
            mCellWidth = w;
            mCellHeight = h;
            mLeftEdgePadding = leftEdgePadding;
            mRightEdgePadding = rightEdgePadding;
            mCellSpacing = intercellSpacing;
        }
        int mColumns;
        int mCellWidth, mCellHeight;
        int mLeftEdgePadding, mRightEdgePadding;
        int mCellSpacing;
    }

    private final LayoutSpec [] mCellSizeChoices = new LayoutSpec[] {
            new LayoutSpec(0, 67, 67, 14, 14, 8),
            new LayoutSpec(0, 92, 92, 14, 14, 8),
    };
    private int mSizeChoice = 1;

    private int mMaxScrollY;

    private final boolean mFling = true;
    private Scroller mScroller = null;

    private GestureDetector mGestureDetector;
    private boolean mLayoutComplete = false;

    public Listener setListener(Listener listener) {
        Listener original = mListener;
        mListener = listener;
        return original;
    }

    private void init(Context context) {
        mPatin.setColor(0xFF000000);

        setVerticalScrollBarEnabled(true);
        initializeScrollbars(context.obtainStyledAttributes(
                android.R.styleable.View));

        mGestureDetector = new GestureDetector(context,
                new SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                if (mScroller != null && !mScroller.isFinished()) {
                    mScroller.forceFinished(true);
                    return false;
                }

                int pos = computeSelectedIndex(e.getX(), e.getY());
                if (pos >= 0 && pos < mAllImages.getCount()) {
                    select(pos, true);
                } else {
                    select(REMOVE_SELCTION, false);
                }
                if (mImageBlockManager != null) {
                    mImageBlockManager.repaintSelection(mCurrentSelection);
                }
                invalidate();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                    float velocityX, float velocityY) {
                final float maxVelocity = 2500;
                if (velocityY > maxVelocity) {
                    velocityY = maxVelocity;
                } else if (velocityY < -maxVelocity) {
                    velocityY = -maxVelocity;
                }

                select(REMOVE_SELCTION, false);
                if (mFling) {
                    mScroller = new Scroller(getContext());
                    mScroller.fling(0, mScrollY, 0, -(int) velocityY, 0, 0, 0,
                            mMaxScrollY);
                    computeScroll();
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                performLongClick();
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                    float distanceX, float distanceY) {
                select(REMOVE_SELCTION, false);
                scrollBy(0, (int) distanceY);
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                select(mCurrentSelection, false);
                int index = computeSelectedIndex(e.getX(), e.getY());
                if (index >= 0 && index < mAllImages.getCount()) {
                    if (mListener != null) mListener.onSelect(index);
                    return true;
                }
                return false;
            }
        });
        // mGestureDetector.setIsLongpressEnabled(false);
    }

    public static interface Listener {
        public void onSelect(int index);
        public void onLayout();
        public void onScroll(int index);
    }

    public GridViewSpecial(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public GridViewSpecial(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GridViewSpecial(Context context) {
        super(context);
        init(context);
    }

    public void setImageList(IImageList list) {
        this.mAllImages = list;
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mMaxScrollY + getHeight();
    }

    public void setSizeChoice(int choice, int scrollY) {
        mSizeChoice = choice;
        clearCache();
        scrollTo(0, scrollY);
        requestLayout();
        invalidate();
    }

    /**
     *
     * @param newSel -2 means use old selection, -1 means remove selection
     * @param newPressed
     */
    public void select(int newSel, boolean newPressed) {
        if (newSel == -2) {
            newSel = mCurrentSelection;
        }
        int oldSel = mCurrentSelection;
        if ((oldSel == newSel) && (mCurrentSelectionPressed == newPressed)) {
            return;
        }

        mShowSelection = (newSel != REMOVE_SELCTION);
        mCurrentSelection = newSel;
        mCurrentSelectionPressed = newPressed;
        if (mImageBlockManager != null) {
            mImageBlockManager.repaintSelection(oldSel);
            mImageBlockManager.repaintSelection(newSel);
        }

        if (newSel != REMOVE_SELCTION) {
            ensureVisible(newSel);
        }
    }

    public void scrollToImage(int index) {
        Rect r = getRectForPosition(index);
        scrollTo(0, r.top);
    }

    private void ensureVisible(int pos) {
        Rect r = getRectForPosition(pos);
        int top = getScrollY();
        int bot = top + getHeight();

        if (r.bottom > bot) {
            mScroller = new Scroller(getContext());
            mScroller.startScroll(mScrollX, mScrollY, 0,
                    r.bottom - getHeight() - mScrollY, 200);
            computeScroll();
        } else if (r.top < top) {
            mScroller = new Scroller(getContext());
            mScroller.startScroll(mScrollX, mScrollY, 0, r.top - mScrollY, 200);
            computeScroll();
        }
        invalidate();
    }

    public void start() {
        mRunning = true;
        requestLayout();
    }

    public void stop() {
        mScroller = null;
        if (mImageBlockManager != null) {
            mImageBlockManager.onPause();
            mImageBlockManager = null;
        }
        mRunning = false;
    }

    public void clearCache() {
        if (mImageBlockManager != null) {
            mImageBlockManager.onPause();
            mImageBlockManager = null;
        }
    }

    @Override
    public void onLayout(boolean changed, int left, int top,
                         int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!mRunning) {
            return;
        }
        clearCache();
        mCurrentSpec = mCellSizeChoices[mSizeChoice];
        int oldColumnCount = mCurrentSpec.mColumns;

        int width = right - left;
        mCurrentSpec.mColumns = 1;
        width -= mCurrentSpec.mCellWidth;
        mCurrentSpec.mColumns += width
                / (mCurrentSpec.mCellWidth + mCurrentSpec.mCellSpacing);

        mCurrentSpec.mLeftEdgePadding = ((right - left)
                - ((mCurrentSpec.mColumns - 1) * mCurrentSpec.mCellSpacing)
                - (mCurrentSpec.mColumns * mCurrentSpec.mCellWidth)) / 2;
        mCurrentSpec.mRightEdgePadding = mCurrentSpec.mLeftEdgePadding;

        int rows = (mAllImages.getCount() + mCurrentSpec.mColumns - 1)
                / mCurrentSpec.mColumns;
        mMaxScrollY = mCurrentSpec.mCellSpacing
                + (rows
                * (mCurrentSpec.mCellSpacing + mCurrentSpec.mCellHeight))
                - (bottom - top) ;
        if (mImageBlockManager == null) {
            mImageBlockManager = new ImageBlockManager();
            mImageBlockManager.moveDataWindow(true);
        }
        mLayoutComplete = true;
        if (mListener != null) mListener.onLayout();
    }

    class ImageBlockManager {
        private final ImageLoader mLoader;
        private int mBlockCacheFirstBlockNumber = 0;

        // mBlockCache is an array with a starting point which is not
        // necessarily zero.  The first element of the array is indicated by
        // mBlockCacheStartOffset.
        private int mBlockCacheStartOffset = 0;
        private ImageBlock [] mBlockCache;

        private static final int sRowsPerPage    = 6;   // should compute this

        private static final int sPagesPreCache  = 2;
        private static final int sPagesPostCache = 2;

        private int mWorkCounter = 0;
        private boolean mDone = false;

        private Thread mWorkerThread;
        private Bitmap mMissingImageThumbnailBitmap;
        private Bitmap mMissingVideoThumbnailBitmap;

        private Drawable mVideoOverlay;
        private Drawable mVideoMmsErrorOverlay;

        ImageBlockManager() {
            mLoader = new ImageLoader(mHandler, 1);

            mBlockCache = new ImageBlock[sRowsPerPage
                    * (sPagesPreCache + sPagesPostCache + 1)];
            for (int i = 0; i < mBlockCache.length; i++) {
                mBlockCache[i] = new ImageBlock();
            }

            mWorkerThread = new Thread(new Runnable() {
                public void run() {
                    while (true) {
                        int workCounter;
                        synchronized (ImageBlockManager.this) {
                            workCounter = mWorkCounter;
                        }
                        if (mDone) {
                            if (mLoader != null) {
                                mLoader.stop();
                            }
                            if (mBlockCache != null) {
                                for (int i = 0; i < mBlockCache.length; i++) {
                                    ImageBlock block = mBlockCache[i];
                                    if (block != null) {
                                        block.recycleBitmaps();
                                        mBlockCache[i] = null;
                                    }
                                }
                            }
                            mBlockCache = null;
                            mBlockCacheStartOffset = 0;
                            mBlockCacheFirstBlockNumber = 0;

                            break;
                        }

                        loadNext();

                        synchronized (ImageBlockManager.this) {
                            if ((workCounter == mWorkCounter) && (!mDone)) {
                                try {
                                    ImageBlockManager.this.wait();
                                } catch (InterruptedException ex) {
                                }
                            }
                        }
                    } // while
                } // run
            });
            BitmapManager.instance().allowThreadDecoding(mWorkerThread);
            mWorkerThread.setName("image-block-manager");
            mWorkerThread.start();
        }

        // Create this bitmap lazily, and only once for all the ImageBlocks to
        // use
        public Bitmap getErrorBitmap(IImage image) {
            if (ImageManager.isImage(image)) {
                if (mMissingImageThumbnailBitmap == null) {
                    mMissingImageThumbnailBitmap =
                            BitmapFactory.decodeResource(
                            GridViewSpecial.this.getResources(),
                            R.drawable.ic_missing_thumbnail_picture);
                }
                return mMissingImageThumbnailBitmap;
            } else {
                if (mMissingVideoThumbnailBitmap == null) {
                    mMissingVideoThumbnailBitmap =
                            BitmapFactory.decodeResource(
                            GridViewSpecial.this.getResources(),
                            R.drawable.ic_missing_thumbnail_video);
                }
                return mMissingVideoThumbnailBitmap;
            }
        }

        private ImageBlock getBlockForPos(int pos) {
            synchronized (ImageBlockManager.this) {
                int blockNumber = pos / mCurrentSpec.mColumns;
                int delta = blockNumber - mBlockCacheFirstBlockNumber;
                if (delta >= 0 && delta < mBlockCache.length) {
                    int index = (mBlockCacheStartOffset + delta)
                            % mBlockCache.length;
                    ImageBlock b = mBlockCache[index];
                    return b;
                }
            }
            return null;
        }

        private void repaintSelection(int pos) {
            synchronized (ImageBlockManager.this) {
                ImageBlock b = getBlockForPos(pos);
                if (b != null) {
                    b.repaintSelection();
                }
            }
        }

        private void onPause() {
            synchronized (ImageBlockManager.this) {
                mDone = true;
                ImageBlockManager.this.notify();
            }
            if (mWorkerThread != null) {
                try {
                    BitmapManager.instance()
                            .cancelThreadDecoding(mWorkerThread);
                    mWorkerThread.join();
                    mWorkerThread = null;
                } catch (InterruptedException ex) {
                    //
                }
            }
            Log.v(TAG, "/ImageBlockManager.onPause");
        }

        void getVisibleRange(int [] range) {
            synchronized (ImageBlockManager.this) {
                int blockLength = mBlockCache.length;
                boolean lookingForStart = true;
                ImageBlock prevBlock = null;
                for (int i = 0; i < blockLength; i++) {
                    int index = (mBlockCacheStartOffset + i) % blockLength;
                    ImageBlock block = mBlockCache[index];
                    if (lookingForStart) {
                        if (block.mIsVisible) {
                            range[0] = block.mBlockNumber
                                    * mCurrentSpec.mColumns;
                            lookingForStart = false;
                        }
                    } else {
                        if (!block.mIsVisible || i == blockLength - 1) {
                            range[1] = (prevBlock.mBlockNumber
                                    * mCurrentSpec.mColumns)
                                    + mCurrentSpec.mColumns - 1;
                            break;
                        }
                    }
                    prevBlock = block;
                }
            }
        }

        private void loadNext() {
            final int blockHeight = (mCurrentSpec.mCellSpacing
                    + mCurrentSpec.mCellHeight);

            final int firstVisBlock =
                    Math.max(0, (mScrollY - mCurrentSpec.mCellSpacing)
                    / blockHeight);
            final int lastVisBlock =
                    (mScrollY - mCurrentSpec.mCellSpacing + getHeight())
                    / blockHeight;

            synchronized (ImageBlockManager.this) {
                ImageBlock [] blocks = mBlockCache;
                int numBlocks = blocks.length;
                int first = (mBlockCacheStartOffset
                        + (firstVisBlock - mBlockCacheFirstBlockNumber))
                        % blocks.length;
                for (int i = 0; i < numBlocks; i++) {
                    int j = first + i;
                    if (j >= numBlocks) {
                        j -= numBlocks;
                    }
                    ImageBlock b = blocks[j];
                    if (b.startLoading() > 0) {
                        break;
                    }
                }
            }
        }

        private void moveDataWindow(boolean forceRefresh) {
            final int blockHeight = (mCurrentSpec.mCellSpacing
                    + mCurrentSpec.mCellHeight);

            final int firstVisBlock = (mScrollY - mCurrentSpec.mCellSpacing)
                    / blockHeight;
            final int lastVisBlock =
                    (mScrollY - mCurrentSpec.mCellSpacing + getHeight())
                    / blockHeight;

            final int preCache = sPagesPreCache;
            final int startBlock = Math.max(0,
                    firstVisBlock - (preCache * sRowsPerPage));

            synchronized (ImageBlockManager.this) {
                boolean any = false;
                ImageBlock [] blocks = mBlockCache;
                int numBlocks = blocks.length;

                int delta = startBlock - mBlockCacheFirstBlockNumber;

                mBlockCacheFirstBlockNumber = startBlock;
                if (Math.abs(delta) > numBlocks || forceRefresh) {
                    for (int i = 0; i < numBlocks; i++) {
                        int blockNum = startBlock + i;
                        blocks[i].setStart(blockNum);
                        any = true;
                    }
                    mBlockCacheStartOffset = 0;
                } else if (delta > 0) {
                    mBlockCacheStartOffset += delta;
                    if (mBlockCacheStartOffset >= numBlocks) {
                        mBlockCacheStartOffset -= numBlocks;
                    }

                    for (int i = delta; i > 0; i--) {
                        int index = (mBlockCacheStartOffset + numBlocks - i)
                                % numBlocks;
                        int blockNum = mBlockCacheFirstBlockNumber
                                + numBlocks - i;
                        blocks[index].setStart(blockNum);
                        any = true;
                    }
                } else if (delta < 0) {
                    mBlockCacheStartOffset += delta;
                    if (mBlockCacheStartOffset < 0) {
                        mBlockCacheStartOffset += numBlocks;
                    }

                    for (int i = 0; i < -delta; i++) {
                        int index = (mBlockCacheStartOffset + i) % numBlocks;
                        int blockNum = mBlockCacheFirstBlockNumber + i;
                        blocks[index].setStart(blockNum);
                        any = true;
                    }
                }

                for (int i = 0; i < numBlocks; i++) {
                    int index = (mBlockCacheStartOffset + i) % numBlocks;
                    ImageBlock block = blocks[index];
                    int blockNum = block.mBlockNumber;
                    boolean isVis = blockNum >= firstVisBlock
                            && blockNum <= lastVisBlock;
                    block.setVisible(isVis);
                }

                if (any) {
                    ImageBlockManager.this.notify();
                    mWorkCounter += 1;
                }
            }
        }

        void doDraw(Canvas canvas) {
            synchronized (ImageBlockManager.this) {
                ImageBlockManager.ImageBlock [] blocks = mBlockCache;
                int blockCount = 0;

                if (blocks[0] == null) {
                    return;
                }

                final int thisHeight = getHeight();
                final int thisWidth  = getWidth();
                final int height = blocks[0].mBitmap.getHeight();
                final int scrollPos = mScrollY;

                int currentBlock = (scrollPos < 0)
                        ? ((scrollPos - height + 1) / height)
                        : (scrollPos / height);

                while (true) {
                    final int yPos = currentBlock * height;
                    if (yPos >= scrollPos + thisHeight) {
                        break;
                    }

                    if (currentBlock < 0) {
                        canvas.drawRect(0, yPos, thisWidth, 0, mPatin);
                        currentBlock += 1;
                        continue;
                    }
                    int effectiveOffset =
                            (mBlockCacheStartOffset
                            + (currentBlock++ - mBlockCacheFirstBlockNumber))
                            % blocks.length;
                    if (effectiveOffset < 0
                            || effectiveOffset >= blocks.length) {
                        break;
                    }

                    ImageBlock block = blocks[effectiveOffset];
                    if (block == null) {
                        break;
                    }
                    synchronized (block) {
                        Bitmap b = block.mBitmap;
                        if (b == null) {
                            break;
                        }
                        canvas.drawBitmap(b, 0, yPos, mPatin);
                        blockCount += 1;
                    }
                }
            }
        }

        int blockHeight() {
            return mCurrentSpec.mCellSpacing + mCurrentSpec.mCellHeight;
        }

        private class ImageBlock {
            Drawable mCellOutline;
            Bitmap mBitmap = Bitmap.createBitmap(getWidth(), blockHeight(),
                    Bitmap.Config.RGB_565);
            Canvas mCanvas = new Canvas(mBitmap);
            Paint mPaint = new Paint();

            int     mBlockNumber;

            // columns which have been requested to the loader
            int     mRequestedMask;

            // columns which have been completed from the loader
            int     mCompletedMask;
            boolean mIsVisible;

            public void dump(StringBuilder line1, StringBuilder line2) {
                synchronized (ImageBlock.this) {
                    line2.append(mCompletedMask != 0xF ? 'L' : '_');
                    line1.append(mIsVisible ? 'V' : ' ');
                }
            }

            ImageBlock() {
                mPaint.setTextSize(14F);
                mPaint.setStyle(Paint.Style.FILL);

                mBlockNumber = REMOVE_SELCTION;
                mCellOutline = GridViewSpecial.this.getResources()
                        .getDrawable(android.R.drawable.gallery_thumb);
            }

            private void recycleBitmaps() {
                synchronized (ImageBlock.this) {
                    mBitmap.recycle();
                    mBitmap = null;
                }
            }

            private void cancelExistingRequests() {
                synchronized (ImageBlock.this) {
                    for (int i = 0; i < mCurrentSpec.mColumns; i++) {
                        int mask = (1 << i);
                        if ((mRequestedMask & mask) != 0) {
                            int pos =
                                    (mBlockNumber * mCurrentSpec.mColumns) + i;
                            if (mLoader.cancel(mAllImages.getImageAt(pos))) {
                                mRequestedMask &= ~mask;
                            }
                        }
                    }
                }
            }

            private void setStart(final int blockNumber) {
                synchronized (ImageBlock.this) {
                    if (blockNumber == mBlockNumber) {
                        return;
                    }

                    cancelExistingRequests();

                    mBlockNumber = blockNumber;
                    mRequestedMask = 0;
                    mCompletedMask = 0;
                    mCanvas.drawColor(0xFF000000);
                    mPaint.setColor(0xFFDDDDDD);
                    int imageNumber = blockNumber * mCurrentSpec.mColumns;
                    int lastImageNumber = mAllImages.getCount() - 1;

                    int spacing = mCurrentSpec.mCellSpacing;
                    int leftSpacing = mCurrentSpec.mLeftEdgePadding;

                    final int yPos = spacing;

                    for (int col = 0; col < mCurrentSpec.mColumns; col++) {
                        if (imageNumber++ >= lastImageNumber) {
                            break;
                        }
                        final int xPos = leftSpacing
                                + (col * (mCurrentSpec.mCellWidth + spacing));
                        mCanvas.drawRect(xPos, yPos,
                                xPos + mCurrentSpec.mCellWidth,
                                yPos + mCurrentSpec.mCellHeight, mPaint);
                        paintSel(0, xPos, yPos);
                    }
                }
            }

            private boolean setVisible(boolean isVis) {
                synchronized (ImageBlock.this) {
                    boolean retval = mIsVisible != isVis;
                    mIsVisible = isVis;
                    return retval;
                }
            }

            private int startLoading() {
                synchronized (ImageBlock.this) {
                    final int startRow = mBlockNumber;
                    int count = mAllImages.getCount();

                    if (startRow == REMOVE_SELCTION) {
                        return 0;
                    }

                    if ((startRow * mCurrentSpec.mColumns) >= count) {
                        return 0;
                    }

                    int retVal = 0;
                    int base = (mBlockNumber * mCurrentSpec.mColumns);
                    for (int col = 0; col < mCurrentSpec.mColumns; col++) {
                        if ((mCompletedMask & (1 << col)) != 0) {
                            continue;
                        }

                        int spacing = mCurrentSpec.mCellSpacing;
                        int leftSpacing = mCurrentSpec.mLeftEdgePadding;
                        final int yPos = spacing;
                        final int xPos = leftSpacing
                                + (col * (mCurrentSpec.mCellWidth + spacing));

                        int pos = base + col;
                        if (pos >= count) {
                            break;
                        }

                        IImage image = mAllImages.getImageAt(pos);
                        if (image != null) {
                            loadImage(base, col, image, xPos, yPos);
                            retVal += 1;
                        }
                    }
                    return retVal;

                }
            }

            Bitmap resizeBitmap(Bitmap b) {
                // assume they're both square for now
                if (b == null || (b.getWidth() == mCurrentSpec.mCellWidth
                        && b.getHeight() == mCurrentSpec.mCellHeight)) {
                    return b;
                }
                float scale = (float) mCurrentSpec.mCellWidth
                        / (float) b.getWidth();
                Matrix m = new Matrix();
                m.setScale(scale, scale, b.getWidth(), b.getHeight());
                Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(),
                        b.getHeight(), m, false);
                return b2;
            }

            private void drawBitmap(IImage image, int base, int baseOffset,
                    Bitmap b, int xPos, int yPos) {
                mCanvas.setBitmap(mBitmap);
                if (b != null) {
                    // if the image is close to the target size then crop,
                    // otherwise scale both the bitmap and the view should be
                    // square but I suppose that could change in the future.
                    int w = mCurrentSpec.mCellWidth;
                    int h = mCurrentSpec.mCellHeight;

                    int bw = b.getWidth();
                    int bh = b.getHeight();

                    int deltaW = bw - w;
                    int deltaH = bh - h;

                    if (deltaW < 10 && deltaH < 10) {
                        int halfDeltaW = deltaW / 2;
                        int halfDeltaH = deltaH / 2;
                        Rect src = new Rect(0 + halfDeltaW,
                                0 + halfDeltaH, bw - halfDeltaW,
                                bh - halfDeltaH);
                        Rect dst = new Rect(xPos, yPos,
                                xPos + w, yPos + h);
                        mCanvas.drawBitmap(b, src, dst, mPaint);
                    } else {
                        Rect src = new Rect(0, 0, bw, bh);
                        Rect dst = new Rect(xPos, yPos, xPos + w, yPos + h);
                        mCanvas.drawBitmap(b, src, dst, mPaint);
                    }
                } else {
                    // If the thumbnail cannot be drawn, put up an error icon
                    // instead
                    Bitmap error = mImageBlockManager.getErrorBitmap(image);
                    int width = error.getWidth();
                    int height = error.getHeight();
                    Rect source = new Rect(0, 0, width, height);
                    int left = (mCurrentSpec.mCellWidth - width) / 2 + xPos;
                    int top = (mCurrentSpec.mCellHeight - height) / 2 + yPos;
                    Rect dest = new Rect(left, top, left + width, top + height);
                    mCanvas.drawBitmap(error, source, dest, mPaint);
                }
                if (ImageManager.isVideo(image)) {
                    Drawable overlay = null;
                    long size = MenuHelper.getImageFileSize(image);
                    if (size >= 0 && size <= mVideoSizeLimit) {
                        if (mVideoOverlay == null) {
                            mVideoOverlay = getResources().getDrawable(
                                    R.drawable.ic_gallery_video_overlay);
                        }
                        overlay = mVideoOverlay;
                    } else {
                        if (mVideoMmsErrorOverlay == null) {
                            mVideoMmsErrorOverlay = getResources().getDrawable(
                                    R.drawable.ic_error_mms_video_overlay);
                        }
                        overlay = mVideoMmsErrorOverlay;
                        Paint paint = new Paint();
                        paint.setARGB(0x80, 0x00, 0x00, 0x00);
                        mCanvas.drawRect(xPos, yPos,
                                xPos + mCurrentSpec.mCellWidth,
                                yPos + mCurrentSpec.mCellHeight, paint);
                    }
                    int width = overlay.getIntrinsicWidth();
                    int height = overlay.getIntrinsicHeight();
                    int left = (mCurrentSpec.mCellWidth - width) / 2 + xPos;
                    int top = (mCurrentSpec.mCellHeight - height) / 2 + yPos;
                    Rect newBounds =
                            new Rect(left, top, left + width, top + height);
                    overlay.setBounds(newBounds);
                    overlay.draw(mCanvas);
                }
                paintSel(base + baseOffset, xPos, yPos);
            }

            private void repaintSelection() {
                int count = mAllImages.getCount();
                int startPos = mBlockNumber * mCurrentSpec.mColumns;
                synchronized (ImageBlock.this) {
                    for (int i = 0; i < mCurrentSpec.mColumns; i++) {
                        int pos = startPos + i;

                        if (pos >= count) {
                            break;
                        }

                        int row = 0; // i / mCurrentSpec.mColumns;
                        int col = i - (row * mCurrentSpec.mColumns);

                        // this is duplicated from getOrKick
                        // (TODO: don't duplicate this code)
                        int spacing = mCurrentSpec.mCellSpacing;
                        int leftSpacing = mCurrentSpec.mLeftEdgePadding;
                        final int yPos = spacing
                                + (row * (mCurrentSpec.mCellHeight + spacing));
                        final int xPos = leftSpacing
                                + (col * (mCurrentSpec.mCellWidth + spacing));

                        paintSel(pos, xPos, yPos);
                    }
                }
            }

            private void paintSel(int pos, int xPos, int yPos) {
                int[] stateSet = EMPTY_STATE_SET;
                if (pos == mCurrentSelection && mShowSelection) {
                    if (mCurrentSelectionPressed) {
                        stateSet = PRESSED_ENABLED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET;
                    } else {
                        stateSet = ENABLED_FOCUSED_SELECTED_WINDOW_FOCUSED_STATE_SET;
                    }
                }

                mCellOutline.setState(stateSet);
                mCanvas.setBitmap(mBitmap);
                mCellOutline.setBounds(xPos, yPos,
                        xPos + mCurrentSpec.mCellWidth,
                        yPos + mCurrentSpec.mCellHeight);
                mCellOutline.draw(mCanvas);
            }

            private void loadImage(
                    final int base,
                    final int baseOffset,
                    final IImage image,
                    final int xPos,
                    final int yPos) {
                synchronized (ImageBlock.this) {
                    final int startBlock = mBlockNumber;
                    final int pos = base + baseOffset;
                    final ImageLoader.LoadedCallback r =
                            new ImageLoader.LoadedCallback() {
                        public void run(Bitmap b) {
                            boolean more = false;
                            synchronized (ImageBlock.this) {
                                if (startBlock != mBlockNumber) {
                                    return;
                                }

                                if (mBitmap == null) {
                                    return;
                                }

                                drawBitmap(image, base, baseOffset, b, xPos,
                                        yPos);

                                int mask = (1 << baseOffset);
                                mRequestedMask &= ~mask;
                                mCompletedMask |= mask;

                                if (mRequestedMask == 0) {
                                    if (mIsVisible) {
                                        postInvalidate();
                                    }
                                    more = true;
                                }
                            }
                            if (b != null) {
                                b.recycle();
                            }

                            if (more) {
                                synchronized (ImageBlockManager.this) {
                                    ImageBlockManager.this.notify();
                                    mWorkCounter += 1;
                                }
                            }
                        }
                    };
                    mRequestedMask |= (1 << baseOffset);
                    mLoader.getBitmap(image, pos, r, mIsVisible, false);
                }
            }
        }
    }

    public void init(Handler handler) {
        mHandler = handler;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (false) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), mPatin);
            return;
        }

        if (mImageBlockManager != null) {
            mImageBlockManager.doDraw(canvas);
            mImageBlockManager.moveDataWindow(false);
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller != null) {
            boolean more = mScroller.computeScrollOffset();
            scrollTo(0, mScroller.getCurrY());
            if (more) {
                postInvalidate();  // So we draw again
            } else {
                mScroller = null;
            }
        } else {
            super.computeScroll();
        }
    }

    // Return the rectange for the thumbnail in the given position.
    Rect getRectForPosition(int pos) {
        LayoutSpec spec = this.mCurrentSpec;
        int row = pos / spec.mColumns;
        int col = pos - (row * spec.mColumns);

        int left = spec.mLeftEdgePadding
                + (col * spec.mCellWidth) + (col * spec.mCellSpacing);
        int top = (row * spec.mCellHeight) + (row * spec.mCellSpacing);

        return new Rect(left, top,
                left + spec.mCellWidth + spec.mCellSpacing,
                top + spec.mCellHeight + spec.mCellSpacing);
    }

    int computeSelectedIndex(float x, float y) {
        int spacing = mCurrentSpec.mCellSpacing;
        int leftSpacing = mCurrentSpec.mLeftEdgePadding;

        int row = (int) (mScrollY + y - spacing)
                / (mCurrentSpec.mCellHeight + spacing);
        int col = Math.min(mCurrentSpec.mColumns - 1,
                (int) (x - leftSpacing) / (mCurrentSpec.mCellWidth + spacing));
        return (row * mCurrentSpec.mColumns) + col;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!canHandleEvent()) {
            return false;
        }
        mGestureDetector.onTouchEvent(ev);
        return true;
    }

    @Override
    public void scrollBy(int x, int y) {
        scrollTo(x, mScrollY + y);
    }

    @Override
    public void scrollTo(int x, int y) {
        y = Math.min(mMaxScrollY, y);
        y = Math.max(0, y);
        if (mListener != null && mCurrentSpec != null) {
            int index = Math.min(mAllImages.getCount(),
                    Math.max(0, computeSelectedIndex(x, y)));
            mListener.onScroll(index);
        }
        super.scrollTo(x, y);
    }

    private boolean canHandleEvent() {
        return mRunning && mLayoutComplete;
    }

    private final Runnable mLongPressCallback = new Runnable() {
        public void run() {
            select(GridViewSpecial.ORIGINAL_SELECT, false);
            showContextMenu();
        }
    };


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!canHandleEvent()) return false;

        boolean handled = true;
        int sel = mCurrentSelection;
        int columns = mCurrentSpec.mColumns;
        int count = mAllImages.getCount();
        boolean pressed = false;
        if (mShowSelection) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (sel != count && (sel % columns < columns - 1)) {
                        sel += 1;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (sel > 0 && (sel % columns != 0)) {
                        sel -= 1;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if ((sel / columns) != 0) {
                        sel -= columns;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if ((sel / columns) != (sel + columns / columns)) {
                        sel = Math.min(count - 1, sel + columns);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    pressed = true;
                    mHandler.postDelayed(mLongPressCallback,
                            ViewConfiguration.getLongPressTimeout());
                    break;
                default:
                    handled = false;
                    break;
            }
        } else {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    int [] range = new int[2];
                    if (mImageBlockManager != null) {
                        mImageBlockManager.getVisibleRange(range);
                        int topPos = range[0];
                        Rect r = getRectForPosition(topPos);
                        if (r.top < getScrollY()) {
                            topPos += columns;
                        }
                        topPos = Math.min(count - 1, topPos);
                        sel = topPos;
                    }
                    break;
                default:
                    handled = false;
                    break;
            }
        }
        if (handled) {
            select(sel, pressed);
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!canHandleEvent()) return false;

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            select(GridViewSpecial.ORIGINAL_SELECT, false);

            // The keyUp doesn't get called when the longpress menu comes up. We
            // only get here when the user lets go of the center key before the
            // longpress menu comes up.
            mHandler.removeCallbacks(mLongPressCallback);

            // open the photo
            if (mListener != null) mListener.onSelect(mCurrentSelection);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }


}
