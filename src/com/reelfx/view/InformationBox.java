package com.reelfx.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.EventObject;

import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.border.LineBorder;

import com.reelfx.Applet;
import com.reelfx.view.util.MoveableWindow;
import com.reelfx.view.util.ViewNotifications;

@SuppressWarnings("serial")
public class InformationBox extends MoveableWindow {

	public final static String NAME = "InformationBox";

	public InformationBox() {
		super();
	}

	@Override
	protected void init() {
		super.init();
		setPreferredSize(new Dimension(250, 200));
		setAlwaysOnTop(true);
		setName(NAME);
		JPanel border = new JPanel();
		border.setBackground(Color.WHITE);
		border.setBorder(new LineBorder(Color.BLACK, 2));
		add(border);
		pack();
		receiveViewNotification(ViewNotifications.CAPTURE_VIEWPORT_CHANGE);
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		super.mousePressed(e);
		Applet.sendViewNotification(ViewNotifications.MOUSE_PRESS_INFO_BOX, e);
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		super.mouseDragged(e);
		Applet.sendViewNotification(ViewNotifications.MOUSE_DRAG_INFO_BOX, e);
	}

	@Override
	public void receiveViewNotification(ViewNotifications notification, Object body) {

		switch(notification) {
		case CAPTURE_VIEWPORT_CHANGE:
			setLocation((int)Applet.CAPTURE_VIEWPORT.getCenterX() - getWidth()/2, (int)Applet.CAPTURE_VIEWPORT.getCenterY() - getHeight()/2);
			break;
		
		case SHOW_ALL:
		case SHOW_INFO_BOX:
			setVisible(true);
			break;
			
		case HIDE_ALL:
		case HIDE_INFO_BOX:
			setVisible(false);
			break;
		/*
		case MOUSE_PRESS_RECORD_CONTROLS:
			super.mousePressed((MouseEvent) body);				
			break;
		
		case MOUSE_DRAG_RECORD_CONTROLS:
			super.mouseDragged((MouseEvent) body);				
			break;
			*/
		}
		
		pack();
	}
}
