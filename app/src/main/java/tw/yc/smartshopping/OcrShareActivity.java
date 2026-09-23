package tw.yc.smartshopping;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public final class OcrShareActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent source = getIntent();
        Intent target = new Intent(this, MainActivity.class)
                .setAction(ShareModeResolver.ACTION_OCR_SCREENSHOT)
                .setType(source.getType())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (source.getClipData() != null) target.setClipData(source.getClipData());
        if (source.hasExtra(Intent.EXTRA_STREAM)) {
            android.os.Parcelable stream = source.getParcelableExtra(Intent.EXTRA_STREAM);
            target.putExtra(Intent.EXTRA_STREAM, stream);
        }
        startActivity(target);
        finish();
    }
}
