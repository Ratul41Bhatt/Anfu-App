package com.hjq.demo.ui.dialog;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.aop.SingleClick;
import com.hjq.demo.ui.view.HandwritingBoardView;
import com.hjq.toast.ToastUtils;

import java.io.ByteArrayOutputStream;

public final class SignatureDialog {

    public static final class Builder extends BaseDialog.Builder<SignatureDialog.Builder> {

        @Nullable
        private SignatureDialog.OnListener mListener;
        private boolean mAutoDismiss = true;
        private final TextView mTitleView;
        private final ImageView mCloseView;
        private final HandwritingBoardView mHandwritingBoardView;
        private final Button mConfirmBtn;
        private final Button mRewriteBtn;

        public Builder(Context context) {
            super(context);
            setContentView(R.layout.signature_dialog);
            setCancelable(false);
            mTitleView = findViewById(R.id.tv_signature_title);
            mCloseView = findViewById(R.id.iv_signature_close);
            mHandwritingBoardView = findViewById(R.id.ll_sign_panel);
            mConfirmBtn = findViewById(R.id.btn_sign_confirm);
            mRewriteBtn = findViewById(R.id.btn_sign_rewrite);
            setOnClickListener(mConfirmBtn);
            setOnClickListener(mRewriteBtn);
            setOnClickListener(mCloseView);
        }

        public SignatureDialog.Builder setTitle(@StringRes int id) {
            return setTitle(getString(id));
        }

        public SignatureDialog.Builder setTitle(CharSequence title) {
            mTitleView.setText(title);
            return this;
        }

        public SignatureDialog.Builder setAutoDismiss(boolean dismiss) {
            mAutoDismiss = dismiss;
            return this;
        }

        public SignatureDialog.Builder setListener(SignatureDialog.OnListener listener) {
            mListener = listener;
            return this;
        }

        public void redo(){
            if(mHandwritingBoardView != null){
                mHandwritingBoardView.redo();
            }
        }

        @SingleClick
        @Override
        public void onClick(View view) {
            switch (view.getId()){
                case R.id.iv_signature_close://关闭对话框
                    if (mAutoDismiss) {
                        dismiss();
                    }
                    if (mListener == null) {
                        return;
                    }
                    mListener.onCancel(getDialog());
                    break;
                case R.id.btn_sign_confirm://确定
                    byte[] byteArray = null;
                    if(mHandwritingBoardView.getBitmap() != null){
                        Bitmap bitmap = mHandwritingBoardView.getBitmap();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        /*下面方法表示压缩图片，中间的值越小，压缩比例越大，失真也约厉害，100表示不压缩*/
                        bitmap.compress(Bitmap.CompressFormat.PNG,100,baos);
                        byteArray = baos.toByteArray();
                    }

                    if(mListener != null){
                        mListener.onConfirmClick(getDialog(),byteArray);
                    }
                    break;
                case R.id.btn_sign_rewrite://重写
                    mHandwritingBoardView.redo();
                    break;
            }
        }
    }

    public interface OnListener {
        /**
         * 点击取消时回调
         */
        void onCancel(BaseDialog dialog);

        /**
         * 确定签名回调
         */
        void onConfirmClick(BaseDialog dialog, byte[] byteArray);
    }

}
