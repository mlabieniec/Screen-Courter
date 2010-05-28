package com.reelfx.view.util;

/**
 * List of notifications that can be sent to the view
 * 
 * @author daniel
 * 
 * Helpful notes on enums: http://java.sun.com/j2se/1.5.0/docs/guide/language/enums.html
 *
 */
public enum ViewNotifications {
	MOUSE_PRESS_INFO_BOX,
	MOUSE_RELEASE_INFO_BOX,
	MOUSE_DRAG_INFO_BOX,
	
	MOUSE_PRESS_RECORD_CONTROLS,
	MOUSE_RELEASE_RECORD_CONTROLS,
	MOUSE_DRAG_RECORD_CONTROLS,
	
	MOUSE_PRESS_CROP_HANDLE,
	MOUSE_DRAG_CROP_HANDLE,
	
	CAPTURE_VIEWPORT_CHANGE,
	
	HIDE_CROP_HANDLES,
	SHOW_CROP_HANDLES,
	
	HIDE_INFO_BOX,
	SHOW_INFO_BOX,
	
	HIDE_CROP_LINES,
	SHOW_CROP_LINES,
	
	HIDE_RECORD_CONTROLS,
	SHOW_RECORD_CONTROLS,
	
	SHOW_ALL,
	HIDE_ALL
}
