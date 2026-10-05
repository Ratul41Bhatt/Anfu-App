package com.hjq.demo.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.demo.R;
import com.hjq.demo.action.ToastAction;

import java.util.List;

public class TextImageAdapter extends ArrayAdapter<String> implements ToastAction {
    private Context context;
    private int resource;
    private List<String> items;
    PrintCallback printCallback = null;


    public TextImageAdapter(Context context, int resource, List<String> items, PrintCallback printCallback) {
        super(context, resource, items);
        this.context = context;
        this.resource = resource;
        this.items = items;
        this.printCallback = printCallback;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        String item = items.get(position);

        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(resource, parent, false);
            holder = new ViewHolder();
            holder.textView = convertView.findViewById(R.id.tv_list_item);
            holder.imageView = convertView.findViewById(R.id.img_print);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.textView.setText(item);
        holder.imageView.setOnClickListener(v -> onButtonClick(position));

        return convertView;
    }
    private void onButtonClick(int position) {
        // 处理按钮点击事件
        //Toast.makeText(context, "打印中...", Toast.LENGTH_SHORT).show();
        //toast("打印中...");
        if(printCallback != null) {
            printCallback.printTest(position);
        }
    }

    static class ViewHolder {
        TextView textView;
        ImageView imageView;
    }

    public interface PrintCallback
    {
        void printTest(int position);
    }
}
