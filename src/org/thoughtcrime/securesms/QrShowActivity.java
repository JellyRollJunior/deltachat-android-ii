package org.thoughtcrime.securesms;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEventCenter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import org.thoughtcrime.securesms.connect.ApplicationDcContext;
import org.thoughtcrime.securesms.connect.DcHelper;

public class QrShowActivity extends AppCompatActivity implements DcEventCenter.DcEventDelegate {

    public final static int WHITE = 0xFFFFFFFF;
    public final static int BLACK = 0xFF000000;
    public final static int WIDTH = 400;
    public final static int HEIGHT = 400;
    public final static String CHAT_ID = "chat_id";

    public int numJoiners;

    DcEventCenter dcEventCenter;

    ApplicationDcContext dcContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_show);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // keeping the screen on also avoids falling back from IDLE to POLL

        dcContext = DcHelper.getContext(this);
        dcEventCenter = dcContext.eventCenter;

        Bundle extras = getIntent().getExtras();
        int chatId = 0;
        if (extras != null) {
            chatId = extras.getInt(CHAT_ID);
        }

        ActionBar supportActionBar = getSupportActionBar();
        assert supportActionBar != null;
        supportActionBar.setDisplayHomeAsUpEnabled(true);

        String hint;
        if (chatId != 0) {
            // verified-group
            String groupName = dcContext.getChat(chatId).getName();
            hint = String.format(this.getString(R.string.qr_show_activity__join_verified_group_hint), groupName);
            supportActionBar.setTitle(groupName);
            supportActionBar.setSubtitle(R.string.qr_show_activity__join_verified_group_title);
        } else {
            // verify-contact
            String selfName = DcHelper.get(this, DcHelper.CONFIG_DISPLAY_NAME); // we cannot use MrContact.getDisplayName() as this would result in "Me" instead of
            String nameAndAddress;
            if (selfName.isEmpty()) {
                selfName = DcHelper.get(this, DcHelper.CONFIG_ADDRESS, "unknown");
                nameAndAddress = selfName;
            } else {
                nameAndAddress = String.format("%s (%s)", selfName, DcHelper.get(this, DcHelper.CONFIG_ADDRESS));
            }
            hint = String.format(this.getString(R.string.qr_show_activity__join_contact_hint), nameAndAddress);
            supportActionBar.setTitle(selfName);
            supportActionBar.setSubtitle(R.string.qr_show_activity__join_contact_title);
        }
        TextView hintBelowQr = findViewById(R.id.qrShowHint);
        hintBelowQr.setText(Html.fromHtml(hint));

        numJoiners = 0;

        ImageView imageView = findViewById(R.id.qrImage);
        try {
            Bitmap bitmap = encodeAsBitmap(dcContext.getSecurejoinQr(chatId));
            imageView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }

        dcEventCenter.addObserver(DcContext.DC_EVENT_SECUREJOIN_INVITER_PROGRESS, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dcEventCenter.removeObservers(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return false;
    }

    Bitmap encodeAsBitmap(String str) throws WriterException {
        BitMatrix result;
        try {
            result = new MultiFormatWriter().encode(str,
                    BarcodeFormat.QR_CODE, WIDTH, HEIGHT, null);
        } catch (IllegalArgumentException iae) {
            // Unsupported format
            return null;
        }

        int w = result.getWidth();
        int h = result.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);

        int configQrOverlayLogo = DcHelper.getInt(this, DcHelper.CONFIG_QR_OVERLAY_LOGO, 1);
        if (configQrOverlayLogo != 0) {
            Bitmap overlay = BitmapFactory.decodeResource(this.getResources(), R.drawable.qr_overlay);
            putOverlay(bitmap, overlay);
        }

        return bitmap;
    }

    public void putOverlay(Bitmap bitmap, Bitmap overlay) {
        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();
        int ow = bw / 6;
        int oh = bh / 6;

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(overlay, null, new Rect(bw / 2 - ow / 2, bh / 2 - oh / 2, bw / 2 + ow / 2, bh / 2 + oh / 2), paint);
    }

    @Override
    public void handleEvent(int eventId, Object data1, Object data2) {
        if (eventId == DcContext.DC_EVENT_SECUREJOIN_INVITER_PROGRESS) {
            DcContext dcContext = DcHelper.getContext(this);
            int contact_id = ((Long) data1).intValue();
            long progress = (Long) data2;
            String msg = null;
            if (progress == 300) {
                msg = String.format(getString(R.string.qr_show_activity__joining), dcContext.getContact(contact_id).getNameNAddr());
                numJoiners++;
            } else if (progress == 600) {
                msg = String.format(getString(R.string.qr_show_activity__verified), dcContext.getContact(contact_id).getNameNAddr());
            } else if (progress == 800) {
                msg = String.format(getString(R.string.qr_show_activity__group_joined), dcContext.getContact(contact_id).getNameNAddr());
            }

            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }

            if (progress == 1000) {
                numJoiners--;
                if (numJoiners <= 0) {
                    finish();
                }
            }
        }

    }
}
