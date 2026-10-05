package com.hjq.demo.ui.view;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hjq.demo.R;

import org.apache.commons.lang3.StringUtils;

public class KeyBoardView extends LinearLayout implements OnClickListener{


	private TextView tv_0;
	private TextView tv_1;
	private TextView tv_2;
	private TextView tv_3;
	private TextView tv_4;
	private TextView tv_5;
	private TextView tv_6;
	private TextView tv_7;
	private TextView tv_8;
	private TextView tv_9;
	private TextView tv_cancel;
	private TextView tv_clear;
	private TextView tv_confirm;
	
	private IKeyBoardListener listener;
	private Context mContext;

    /** Visual actions for the Home amount dialog; callbacks stay unchanged. */
    public void styleAmountActions() {
        styleAmountAction(tv_cancel, R.drawable.bg_amount_backspace,
                R.drawable.ic_amount_backspace, android.graphics.Color.rgb(31, 41, 55));
        styleAmountAction(tv_clear, R.drawable.bg_amount_clear,
                R.drawable.ic_amount_clear, android.graphics.Color.WHITE);
    }

    private void styleAmountAction(TextView key, int background, int icon, int textColor) {
        int padding = Math.round(10 * getResources().getDisplayMetrics().density);
        key.setBackgroundResource(background);
        key.setTextColor(textColor);
        key.setTextSize(12);
        key.setGravity(android.view.Gravity.CENTER);
        key.setPadding(0, padding, 0, padding);
        key.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
        key.setContentDescription(key.getText());
    }

	public void setTv_cancel(String name) {
		if(!StringUtils.isEmpty(name)){
			this.tv_cancel.setText(name);
		}
	}

	public void setTv_clear(String name) {
		if(!StringUtils.isEmpty(name)){
			this.tv_clear.setText(name);
		}
	}

	public void setTv_confirm(String name) {
		if(!StringUtils.isEmpty(name)){
			this.tv_confirm.setText(name);
		}
	}
	
	public KeyBoardView(Context context) {
		this(context, null);
	}

	public KeyBoardView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public KeyBoardView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
		this.mContext = context;
		initView();
	}

	private void initView() {
		// TODO Auto-generated method stub
		View view = View.inflate(mContext, R.layout.layout_number, null);
		tv_0 = (TextView) view.findViewById(R.id.kb_bt_zero);
		tv_1 = (TextView) view.findViewById(R.id.kb_bt_one);
		tv_2 = (TextView) view.findViewById(R.id.kb_bt_two);
		tv_3 = (TextView) view.findViewById(R.id.kb_bt_three);
		tv_4 = (TextView) view.findViewById(R.id.kb_bt_four);
		tv_5 = (TextView) view.findViewById(R.id.kb_bt_five);
		tv_6 = (TextView) view.findViewById(R.id.kb_bt_six);
		tv_7 = (TextView) view.findViewById(R.id.kb_bt_seven);
		tv_8 = (TextView) view.findViewById(R.id.kb_bt_eight);
		tv_9 = (TextView) view.findViewById(R.id.kb_bt_nine);
		tv_cancel = (TextView) view.findViewById(R.id.kb_bt_cancel);
		tv_clear = (TextView) view.findViewById(R.id.kb_bt_clear);
		tv_confirm = (TextView) view.findViewById(R.id.kb_bt_ensure);
		addView(view, new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT));

		initEvent();
	}

	private void initEvent() {
		// TODO Auto-generated method stub
		tv_0.setOnClickListener(this);
		tv_1.setOnClickListener(this);
		tv_2.setOnClickListener(this);
		tv_3.setOnClickListener(this);
		tv_4.setOnClickListener(this);
		tv_5.setOnClickListener(this);
		tv_6.setOnClickListener(this);
		tv_7.setOnClickListener(this);
		tv_8.setOnClickListener(this);
		tv_9.setOnClickListener(this);
		tv_cancel.setOnClickListener(this);
		tv_clear.setOnClickListener(this);
		tv_confirm.setOnClickListener(this);
	}

	public void setNumberPadClickable(boolean clickBle) {
		tv_0.setClickable(clickBle);
		tv_1.setClickable(clickBle);
		tv_2.setClickable(clickBle);
		tv_3.setClickable(clickBle);
		tv_4.setClickable(clickBle);
		tv_5.setClickable(clickBle);
		tv_6.setClickable(clickBle);
		tv_7.setClickable(clickBle);
		tv_8.setClickable(clickBle);
		tv_9.setClickable(clickBle);
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		String s = "";
		switch (v.getId()) {
		case R.id.kb_bt_zero:
			s = tv_0.getText().toString();
			break;

		case R.id.kb_bt_one:
			s = tv_1.getText().toString();
			break;

		case R.id.kb_bt_two:
			s = tv_2.getText().toString();
			break;

		case R.id.kb_bt_three:
			s = tv_3.getText().toString();
			break;

		case R.id.kb_bt_four:
			s = tv_4.getText().toString();
			break;

		case R.id.kb_bt_five:
			s = tv_5.getText().toString();
			break;

		case R.id.kb_bt_six:
			s = tv_6.getText().toString();
			break;

		case R.id.kb_bt_seven:
			s = tv_7.getText().toString();
			break;
			
		case R.id.kb_bt_eight:
			s = tv_8.getText().toString();
			break;
			
		case R.id.kb_bt_nine:
			s = tv_9.getText().toString();
			break;
			
		case R.id.kb_bt_cancel:
			s = "-1";
			break;
			
		case R.id.kb_bt_clear:
			s = "-2";
			break;
			
		case R.id.kb_bt_ensure:
			s = "-3";
			break;

		default:
			break;
		}
		
		if(s.equals("-1")){
			listener.cancel();
			
		}else if (s.equals("-2")) {
			listener.clear();
			
		}else if(s.equals("-3")){
			listener.confirm();
			
		}else {
			listener.getNumber(s);
		}
	}

	public void setIKetBoardListener(IKeyBoardListener listener){
		this.listener = listener;
	}
	
	public void setButtonClickBle(boolean clickBle){
		tv_confirm.setClickable(clickBle);
		if (clickBle) {
			tv_confirm.setBackgroundResource(R.drawable.kb_tv_ensure_selector);
		}else {
			tv_confirm.setBackgroundColor(getResources().getColor(R.color.grey2));
		}
		
	}
	
	public interface IKeyBoardListener{
		public void getNumber(String s);
		public void clear();
		public void confirm();
		public void cancel();
	}
	

}
