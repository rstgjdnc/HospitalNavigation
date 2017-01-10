package com.example.ncku.hospitalnavigation.utils;

import android.content.Context;

public class StringUtil {
	private static final String SPLIT = "\\|";

	public static String[] getSpinnerItems(Context mContext, int resId) {
		String[] items = mContext.getResources().getStringArray(resId);
		for(int i=0;i<items.length;i++)
			items[i] = items[i].split(SPLIT)[1];
		return items;
	}
	
	public static String getSpinnerValue(Context mContext, int resId, String itemName) {
		String[] items = mContext.getResources().getStringArray(resId);
		for(int i=0;i<items.length;i++) {
			if(itemName.equals(items[i].split(SPLIT)[1]))
				return items[i].split(SPLIT)[0];
		}
		return "0";
	}
}
