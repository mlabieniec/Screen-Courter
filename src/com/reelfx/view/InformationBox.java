package com.reelfx.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.EventObject;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;

import com.reelfx.Applet;
import com.reelfx.view.util.MoveableWindow;
import com.reelfx.view.util.ViewNotifications;

@SuppressWarnings("serial")
public class InformationBox extends MoveableWindow {

	public final static String NAME = "InformationBox";
	
	private final String defaultMessage = "ReelFX Screen Recorder    ";
	private ViewNotifications currentState;
	private JLabel title;
	public InformationBox() {
		super();
	}

	@Override
	protected void init() {
		super.init();
		setPreferredSize(new Dimension(230, 33));
		setAlwaysOnTop(true);
		setName(NAME);
		
		title = new JLabel(defaultMessage);
		title.setFont(new Font("Arial", Font.BOLD, 15));
		JLabel icon = new JLabel(new ImageIcon(this.getClass().getClassLoader().getResource("com/reelfx/view/images/move.png")));
		
		JPanel border = new JPanel();
		border.setBackground(Color.WHITE);
		border.setBorder(new LineBorder(Color.BLACK, 2));
		border.add(title);
		border.add(icon);
		
		add(border);
		pack();
		receiveViewNotification(ViewNotifications.CAPTURE_VIEWPORT_CHANGE);
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		if(currentState == ViewNotifications.DISABLE_ALL) return;
		super.mousePressed(e);
		Applet.sendViewNotification(ViewNotifications.MOUSE_PRESS_INFO_BOX, e);
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		if(currentState == ViewNotifications.DISABLE_ALL) return;
		super.mouseDragged(e);
		Applet.sendViewNotification(ViewNotifications.MOUSE_DRAG_INFO_BOX, e);
	}

	@Override
	public void receiveViewNotification(ViewNotifications notification, Object body) {

		switch(notification) {
		case CAPTURE_VIEWPORT_CHANGE:
			setLocation((int)Applet.CAPTURE_VIEWPORT.getCenterX() - getWidth()/2, (int)Applet.CAPTURE_VIEWPORT.getCenterY() - getHeight()/2);
			break;
		
		case READY:
			if(Applet.IS_MAC && !Applet.DEV_MODE) break; // TODO temporary
		case SHOW_ALL:
		case SHOW_INFO_BOX:
			setAlwaysOnTop(true);
			setVisible(true);
			break;
			
		case DISABLE_ALL:
			setAlwaysOnTop(false);
			break;
			
		case POST_OPTIONS:
		case POST_OPTIONS_NO_UPLOADING:
		case PRE_RECORDING:
		case RECORDING:
		case HIDE_ALL:
		case HIDE_INFO_BOX:
			setVisible(false);
			break;
		
		case MOUSE_DRAG_CROP_HANDLE:
			title.setText(Applet.CAPTURE_VIEWPORT.width+"x"+Applet.CAPTURE_VIEWPORT.height);
			break;
		
		case MOUSE_RELEASE_CROP_HANDLE:
			title.setText(defaultMessage);
			break;
		}
		
		pack();
		currentState = notification;
	}
}
