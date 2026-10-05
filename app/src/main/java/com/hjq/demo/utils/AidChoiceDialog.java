package com.hjq.demo.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.ListView;

import java.util.ArrayList;

public class AidChoiceDialog {

    public interface OnItemSelectedListener {
        void onItemSelected(int position); // position: 选中的条目序号(从1开始), -1表示取消
    }

    public static void showDialog(Context context, String dialogType, ArrayList<TLVModel> tlvModelArrayList, final OnItemSelectedListener listener) {
        // 处理无效输入
        if (tlvModelArrayList.size() <= 0) {
            if (listener != null) listener.onItemSelected(-1);
            return;
        }

        // 生成选项文本 (1-based)
        final String[] items = new String[tlvModelArrayList.size()];
        for (int i = 0; i < tlvModelArrayList.size(); i++) {
            items[i] = dialogType+"Value :\n" + AFUtils.hexToAscii(  tlvModelArrayList.get(i).getValue());
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Please select the application:");

        // 设置单选列表 (默认选中第一项)
        builder.setSingleChoiceItems(items, 0, null);

        // 确定按钮
        builder.setPositiveButton("Commit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                AlertDialog alertDialog = (AlertDialog) dialog;
                ListView listView = alertDialog.getListView();
                int selectedIndex = listView.getCheckedItemPosition(); // 获取0-based索引

                if (listener != null) {
                    // 转换为1-based序号返回
                    listener.onItemSelected(selectedIndex);
                }
            }
        });

        // 取消按钮
        builder.setNegativeButton("Cancle", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (listener != null) listener.onItemSelected(-1);
            }
        });

        // 处理返回键取消
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (listener != null) listener.onItemSelected(-1);
            }
        });

        builder.show();
    }
}
