package com.agmbat.meetyou.widget;

import android.support.v7.widget.RecyclerView;
import android.view.View;

public interface OnRecyclerViewItemClickListener<VH extends RecyclerView.ViewHolder> {

    void onItemClick(View view, int position, VH viewHolder);

    void onLongClick(View view, int position, VH viewHolder);
}
