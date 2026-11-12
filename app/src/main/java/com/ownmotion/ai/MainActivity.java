package com.ownmotion.ai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private ImageView preview;
    private TextView status;
    private EditText prompt;
    private ProgressBar progress;
    private Bitmap selectedBitmap;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10, 15, 30));
        getWindow().setNavigationBarColor(Color.rgb(10, 15, 30));
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(10, 15, 30));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root);

        TextView logo = text("OWNMOTION AI", 25, Color.WHITE, true);
        root.addView(logo);
        TextView sub = text("CPU-first • Private • On-device video studio", 13, 0xff9ba9c7, false);
        root.addView(sub);

        preview = new ImageView(this);
        preview.setBackgroundColor(0xff18233c);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, dp(230));
        imageLp.topMargin = dp(22);
        root.addView(preview, imageLp);

        Button choose = button("১. ছবি নির্বাচন করুন");
        root.addView(choose);
        choose.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, PICK_IMAGE);
        });

        root.addView(text("২. ভিডিওর বর্ণনা", 15, Color.WHITE, true));
        prompt = new EditText(this);
        prompt.setTextColor(Color.WHITE);
        prompt.setHintTextColor(0xff8794ad);
        prompt.setHint("যেমন: cinematic slow camera movement, sunset...");
        prompt.setMinLines(3);
        prompt.setGravity(Gravity.TOP);
        prompt.setPadding(dp(12), dp(12), dp(12), dp(12));
        prompt.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff7186b5));
        LinearLayout.LayoutParams promptLp = new LinearLayout.LayoutParams(-1, -2);
        promptLp.topMargin = dp(8);
        root.addView(prompt, promptLp);

        TextView note = text("প্রথম সংস্করণ: ছবির উপর CPU-তে pan/zoom ও caption দিয়ে MP4 তৈরি করে। এটি এখনো মানুষকে হাঁটানো/দৌড়ানো বা নতুন দৃশ্য তৈরি করা generative AI model নয়।", 12, 0xffffcc80, false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(12);
        root.addView(note, noteLp);

        Button make = button("৩. ভিডিও তৈরি করুন");
        LinearLayout.LayoutParams makeLp = new LinearLayout.LayoutParams(-1, dp(54));
        makeLp.topMargin = dp(18);
        root.addView(make, makeLp);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(5)));
        status = text("প্রস্তুত। একটি ছবি নির্বাচন করুন।", 13, 0xffb8c5df, false);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(12);
        root.addView(status, statusLp);

        make.setOnClickListener(v -> generate());
        setContentView(scroll);
    }

    private void generate() {
        if (selectedBitmap == null) {
            Toast.makeText(this, "আগে একটি ছবি নির্বাচন করুন", Toast.LENGTH_SHORT).show();
            return;
        }
        final Bitmap source = selectedBitmap;
        final String caption = prompt.getText().toString().trim();
        progress.setVisibility(View.VISIBLE);
        status.setText("ভিডিও তৈরি হচ্ছে… ফোন গরম হতে পারে; অ্যাপ খোলা রাখুন।");
        worker.execute(() -> {
            try {
                File dir = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "OwnMotion");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Output folder তৈরি করা যায়নি");
                File out = new File(dir, "OwnMotion_" + System.currentTimeMillis() + ".mp4");
                CpuVideoRenderer.render(source, caption, out, 5);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    status.setText("সম্পন্ন! MP4 সংরক্ষিত: " + out.getAbsolutePath());
                    Toast.makeText(this, "ভিডিও তৈরি হয়েছে", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    status.setText("সমস্যা: " + e.getMessage());
                });
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 2048);
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                Bitmap b = BitmapFactory.decodeStream(in, null, options);
                if (b == null) throw new Exception("ছবি পড়া যায়নি");
                if (selectedBitmap != null) selectedBitmap.recycle();
                selectedBitmap = b;
                preview.setImageBitmap(b);
                status.setText("ছবি নির্বাচন করা হয়েছে: " + b.getWidth() + " × " + b.getHeight());
            } catch (Exception e) {
                Toast.makeText(this, "ছবি খোলা যায়নি: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff385bd6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(14);
        b.setLayoutParams(lp);
        return b;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }
    private int sampleSize(int width, int height, int maxSide) {
        int sample = 1;
        while (width / sample > maxSide || height / sample > maxSide) sample *= 2;
        return Math.max(1, sample);
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    @Override protected void onDestroy() {
        worker.shutdown();
        if (selectedBitmap != null && !selectedBitmap.isRecycled()) selectedBitmap.recycle();
        super.onDestroy();
    }
}
