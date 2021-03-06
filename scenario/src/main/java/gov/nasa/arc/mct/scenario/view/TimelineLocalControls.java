/*******************************************************************************
 * Mission Control Technologies, Copyright (c) 2009-2012, United States Government
 * as represented by the Administrator of the National Aeronautics and Space 
 * Administration. All rights reserved.
 *
 * The MCT platform is licensed under the Apache License, Version 2.0 (the 
 * "License"); you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations under 
 * the License.
 *
 * MCT includes source code licensed under additional open source licenses. See 
 * the MCT Open Source Licenses file included with this distribution or the About 
 * MCT Licenses dialog available at runtime from the MCT Help menu for additional 
 * information. 
 *******************************************************************************/
package gov.nasa.arc.mct.scenario.view;

import gov.nasa.arc.mct.gui.SelectionProvider;
import gov.nasa.arc.mct.gui.View;
import gov.nasa.arc.mct.scenario.component.CostFunctionCapability;
import gov.nasa.arc.mct.scenario.component.DurationCapability;
import gov.nasa.arc.mct.scenario.util.DurationFormatter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.OverlayLayout;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Local controls for timeline-style views. Gives a top and bottom bar showing tick 
 * marks, allowing pan and zoom, and permitting vertical overlays.
 * 
 * @author vwoeltje
 *
 */
public class TimelineLocalControls extends JPanel implements DurationCapability, ChangeListener, SelectionProvider {
	public static final int LEFT_MARGIN = 80;
	public static final int RIGHT_MARGIN = 20;
	
	private static final NumberFormat FORMAT = new DecimalFormat();
	
	private static final double ZOOM_MAX_POWER = 7; // 2 ^ 7
	private static final int SLIDER_MAX = 10000; // For finer resolution
	private static final int TICK_AREA_HEIGHT = 40;
	private static final int CONNECTOR_HEIGHT = 12;
	private static final int PAN_ICON_SIZE = 12;

	private static final long PAN_INTERVAL = 1000L / 50L; // pan at 30 fps
	private static final long PAN_TIME = 200L; // pan for half a second per click
	private static final double PAN_DRAG = 0.85; // slows down pan
	
	private static final Color BACKGROUND_COLOR = Color.white;
	
	private static final long[] TICK_DIVISIONS = // These get multiplied in static block below
		{1, 10, 10, 10, // Up to 1000 ms -> 1s
		 5, 3,  2,  2,  // Up to   60 s  -> 1m
		 5, 3,  2,  2,  // Up to   60 m  -> 1h
		 3, 2,  2,  2,  // Up to   24 h  -> 1d
		 7,             // Up to    7 d  -> 1w
		};
	private static final Map<Long, String> NAMED_TICK_SIZES = new HashMap<Long, String>();
	
	//public static final DateFormat DURATION_FORMAT = new SimpleDateFormat("ddd HH:mm");
	static {
		//DURATION_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
		for (int i = 1; i < TICK_DIVISIONS.length; i++) {
			TICK_DIVISIONS[i] *= TICK_DIVISIONS[i-1];
		}
		NAMED_TICK_SIZES.put(1000L, "Seconds");
		NAMED_TICK_SIZES.put(1000L * 60L, "Minutes");
		NAMED_TICK_SIZES.put(1000L * 60L * 60L, "Hours");
		NAMED_TICK_SIZES.put(1000L * 60L * 60L * 24L, "Days");
		NAMED_TICK_SIZES.put(1000L * 60L * 60L * 24L * 7L, "Weeks");		
	}
	
	private static final long serialVersionUID = 5844637696012429283L;
	
	//private DurationCapability masterDuration;
	private JComponent contentPane = new JPanel(new GridLayout(1,1));
	private TimelineLocalControls controlParent = null;
	
	private TimelineOverlay overlay = new TimelineOverlay();
	private long centerTime;
	
	private static final Color EDGE_COLOR = new Color(228, 240, 255);
	private static final Color SLIDER_COLOR = EDGE_COLOR.brighter();	
	private static final Color TRACK_COLOR = EDGE_COLOR.darker();	
	private static final Color OVERLAY_COLOR = new Color(0,128,255,180);
	private static final Color OVERLAY_TEXT_COLOR = Color.WHITE;
	
	private JSlider zoomControl;
	private JLabel durationLabel = new JLabel();
	private MultiSlider compositeControl = new MultiSlider();
	
	private JLabel timeLabel = new JLabel();
	
	private Collection<ChangeListener> changeListeners = new HashSet<ChangeListener>();
	
	// Start and end time of master duration
	private long start;
	private long end;
	
	private View selectedView = null;

	public TimelineLocalControls(DurationCapability masterDuration) {
		super(new BorderLayout());
		//this.masterDuration = masterDuration;
		
		start = Math.min(start, masterDuration.getStart());
		end = Math.max(end, masterDuration.getEnd());
		centerTime = (start + end) / 2;
		
		setOpaque(false);
		add(makeMiddlePanel(false), BorderLayout.CENTER);
		updateLabels();
		
		this.addAncestorListener(new AncestorListener() {
			@Override
			public void ancestorAdded(AncestorEvent event) {
				updateAncestor(event);
			}

			@Override
			public void ancestorRemoved(AncestorEvent event) {
				updateAncestor(event);
			}

			@Override
			public void ancestorMoved(AncestorEvent event) {
				//updateAncestor();
			}			
		});
	}
	
	public void updateMasterDuration(DurationCapability masterDuration) {	
		if (controlParent != null) {
			controlParent.updateMasterDuration(masterDuration);
		} else {
			start = Math.min(start, masterDuration.getStart());
			end = Math.max(end, masterDuration.getEnd());
			stateChanged(null);
		}
	}
	
	private void updateAncestor(AncestorEvent event) {
		TimelineLocalControls newParent = (TimelineLocalControls) 
				SwingUtilities.getAncestorOfClass(TimelineLocalControls.class, this);
		
		boolean isVisible = isVisible();
		
		// Make sure new parent is reachable after the event resolves
		if (event.getID() == AncestorEvent.ANCESTOR_REMOVED) {
			Container container = this.getParent();
			while (container != newParent && container != null) {
				isVisible &= container.isVisible();
				if (container == event.getAncestorParent()) {
					newParent = null;
					break;
				}
				container = container.getParent();
			}
		}
		
		boolean isTopLevelControl = newParent == null && isVisible;
		
		// Determine if the component needs to be rebuilt by 
		// comparing to its current state. To avoid explicit 
		// state tracking, this is inferred from component 
		// count. (A top-level display will have three 
		// children, including top and bottom control areas; 
		// other displays will only have one, the content
		// area.)
		int componentCount = getComponentCount();
		boolean hasChanged = componentCount == 0 ||
				componentCount != (isTopLevelControl ? 3 : 1);
		
		if (hasChanged) {
			removeAll();		
			if (isTopLevelControl) {
				add(makeUpperPanel(), BorderLayout.NORTH);
				add(makeLowerPanel(), BorderLayout.SOUTH);
			}
			
			add(makeMiddlePanel(isTopLevelControl), BorderLayout.CENTER);
			overlay.setVisible(false);
			contentPane.setOpaque(isTopLevelControl);
		}
		
		// Start listening to the new parent, if parent changed
		if (newParent != controlParent) {
			if (controlParent != null) {
				controlParent.removeChangeListener(this);
			}
			if (newParent != null) {
				newParent.addChangeListener(this);	
			}
			controlParent = newParent;
			stateChanged(new ChangeEvent(event.getSource()));
		}
	}
	
	/**
	 * Get the component in which content can be placed. In practice, 
	 * this should be populated with the view-specific part of sub-classes; 
	 * that is, stuff other than the common timeline local controls 
	 * should be added to the content pane.
	 * @return the content area for this view
	 */
	public JComponent getContentPane() {
		return contentPane;
	}
	
	
	private JComponent makeMiddlePanel(boolean isTopLevel) {
		JPanel midPanel = new JPanel();// new JPanel(new GridLayout(1,1));//springLayout);
		midPanel.setLayout(new OverlayLayout(midPanel));
		midPanel.setBackground(BACKGROUND_COLOR);
		midPanel.add(overlay);
		midPanel.add(contentPane);
		midPanel.setOpaque(false);
		
		overlay.setVisible(false);
		
		if (isTopLevel) {
			JComponent pane = new JScrollPane(midPanel);
			pane.setBorder(BorderFactory.createEmptyBorder());
			return pane;
		} else {
			return midPanel;
		}
	}
	
	private JComponent makeUpperPanel() {
		JPanel upperPanel = new JPanel(new BorderLayout());
		
		durationLabel = new JLabel();
		zoomControl = new JSlider(0, SLIDER_MAX, 0);
		zoomControl.setOpaque(false);
		zoomControl.addChangeListener(this);
		
		upperPanel.add(durationLabel, BorderLayout.WEST);
		upperPanel.add(zoomControl, BorderLayout.EAST);
		upperPanel.setBackground(EDGE_COLOR);
		
		upperPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, EDGE_COLOR.darker()),				
				BorderFactory.createEmptyBorder(4, 4, 4, 4))); //TODO: Move to constant
		
		durationLabel.setText("Total Duration: " + DurationFormatter.formatDuration(end - start));
		
		return upperPanel;
	}
	
	private JComponent makeLowerPanel() {
		SpringLayout springLayout = new SpringLayout();
		JPanel lowerPanel = new JPanel(springLayout);
		
		JPanel tickPanel = new TickMarkPanel();
		tickPanel.addMouseListener(overlay);
		tickPanel.addMouseMotionListener(overlay);
		JButton leftButton = new JButton(new PanIcon(-1));
		leftButton.setOpaque(false);
        leftButton.setContentAreaFilled(false);
        leftButton.setBorder(BorderFactory.createEmptyBorder());
        leftButton.addActionListener(new Panner(-1));
		JButton rightButton = new JButton(new PanIcon(1));
		rightButton.setOpaque(false);
        rightButton.setContentAreaFilled(false);
        rightButton.setBorder(BorderFactory.createEmptyBorder());
        rightButton.addActionListener(new Panner(1));
		tickPanel.setOpaque(false);
		compositeControl.setOpaque(false);
		compositeControl.setBackground(TRACK_COLOR);
		compositeControl.setForeground(SLIDER_COLOR);
		compositeControl.addActionListener(new CompositePanZoomListener());
		JComponent connector = new CompositeControlConnector();
		
		lowerPanel.add(timeLabel);
		lowerPanel.add(tickPanel);
		lowerPanel.add(leftButton);
		lowerPanel.add(rightButton);
		lowerPanel.add(compositeControl);
		lowerPanel.add(connector);
		
		springLayout.putConstraint(SpringLayout.WEST, tickPanel, getLeftPadding(), SpringLayout.WEST, lowerPanel);
		springLayout.putConstraint(SpringLayout.EAST, tickPanel, -getRightPadding(), SpringLayout.EAST, lowerPanel);
		
		springLayout.putConstraint(SpringLayout.EAST, leftButton, 0, SpringLayout.WEST, tickPanel);
		springLayout.putConstraint(SpringLayout.WEST, rightButton, 0, SpringLayout.EAST, tickPanel);
		springLayout.putConstraint(SpringLayout.EAST, timeLabel, 0, SpringLayout.WEST, leftButton);
		
		springLayout.putConstraint(SpringLayout.NORTH, tickPanel, 0, SpringLayout.NORTH, lowerPanel);
		springLayout.putConstraint(SpringLayout.SOUTH, tickPanel, 0, SpringLayout.NORTH, connector);
		springLayout.putConstraint(SpringLayout.SOUTH, connector, 0, SpringLayout.NORTH, compositeControl);
		springLayout.putConstraint(SpringLayout.NORTH, connector, TICK_AREA_HEIGHT, SpringLayout.NORTH, lowerPanel);
		springLayout.putConstraint(SpringLayout.NORTH, compositeControl, CONNECTOR_HEIGHT, SpringLayout.SOUTH, tickPanel);
		springLayout.putConstraint(SpringLayout.SOUTH, lowerPanel, CONNECTOR_HEIGHT/4, SpringLayout.SOUTH, compositeControl);
		
		springLayout.putConstraint(SpringLayout.WEST, compositeControl, getLeftPadding(), SpringLayout.WEST, lowerPanel);
		springLayout.putConstraint(SpringLayout.EAST, compositeControl, -getRightPadding(), SpringLayout.EAST, lowerPanel);		
		
		springLayout.putConstraint(SpringLayout.WEST, connector, 0, SpringLayout.WEST, compositeControl);
		springLayout.putConstraint(SpringLayout.EAST, connector, 0, SpringLayout.EAST, compositeControl);		

		
		springLayout.putConstraint(SpringLayout.VERTICAL_CENTER, timeLabel, 0, SpringLayout.VERTICAL_CENTER, lowerPanel);
		springLayout.putConstraint(SpringLayout.VERTICAL_CENTER, leftButton, 0, SpringLayout.VERTICAL_CENTER, lowerPanel);
		springLayout.putConstraint(SpringLayout.VERTICAL_CENTER, rightButton, 0, SpringLayout.VERTICAL_CENTER, lowerPanel);
		
		lowerPanel.setBackground(EDGE_COLOR);
		lowerPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, EDGE_COLOR.darker()));
		
		return lowerPanel;
	}	
	
	private void updateCompositeControl() {
		float span = (float) (end - start);
		float low = ((float) (getStart() - start)) / span;
		float high = ((float) (getEnd() - start)) / span;
		compositeControl.setSelectedProportions(low, high);
	}
	
	private void updateLabels() {
		String label = NAMED_TICK_SIZES.get(findNamedTickSize());
		if (label == null) {
			label = "???"; // TODO: Log
		}

		timeLabel.setText(label);
		durationLabel.setText("Total Duration: " + DurationFormatter.formatDuration(end - start));
	}
	
	@Override
	public long getStart() {
		return controlParent != null ? controlParent.getStart() : 
			centerTime - (long) (((double) (end - start) / getZoom()) / 2.0);//masterDuration.getStart();
	}

	@Override
	public long getEnd() {
		return controlParent != null ? controlParent.getEnd() :
			centerTime + (long) (((double) (end - start) / getZoom()) / 2.0);//masterDuration.getStart();
	}

	@Override
	public void setStart(long start) {
		this.start = start;		
		//masterDuration.setStart(start); //TODO: Don't delegate
	}

	@Override
	public void setEnd(long end) {
		this.end = end;
		//masterDuration.setEnd(end); //TODO: Don't delegate
	}
	
	/**
	 * Compute the maximum time that can be at the center of this component without 
	 * leaving empty space to the right. This is used to bound panning behavior
	 * @return the maximum time value that can be at the center of the view
	 */
	private long getMaximumCenter() {
		return end - (long) ((getWidth() - getLeftPadding() - getRightPadding()) / getPixelScale()) / 2;
	}

	/**
	 * Compute the minimum time that can be at the center of this component without 
	 * leaving empty space to the left. This is used to bound panning behavior
	 * @return the minimum time value that can be at the center of the view
	 */
	private long getMinimumCenter() {
		return start + (long) ((getWidth() - getLeftPadding() - getRightPadding()) / getPixelScale()) / 2;
	}
	
	/**
	 * Get the pixel scale currently being displayed. This is taken as the number of 
	 * pixels per millisecond (generally, this is must less than 1.0)
	 * @return number of pixels in a millisecond
	 */
	public double getPixelScale() {
		return controlParent != null ?
				controlParent.getPixelScale() :
				//getZoom() * Already factored into getEnd() - getStart() below 
				(double) (getWidth() - getLeftPadding() - getRightPadding()) / 
				(double) (getEnd() - getStart());
	}
	
	public long getTimeOffset() {
		return controlParent != null ? controlParent.getTimeOffset() : getStart();
	}
	
	public int getLeftPadding() {
		return TimelineLocalControls.LEFT_MARGIN;
	}
	
	public int getRightPadding() {
		return TimelineLocalControls.RIGHT_MARGIN;
	}
	
	private double getZoom() {
		return zoomControl != null ? 
				Math.pow(2, ((double) zoomControl.getValue()) / ((double) SLIDER_MAX) * ZOOM_MAX_POWER) :
				1.0;
	}
	
	private void setZoom(double value) {
		// v = 2 ^ ( (n / M) * P)
		// log2(v) = (n/M) * P
		// n = (M*log2(v))/P
		int n = (int) ( ((double) SLIDER_MAX * (Math.log(value) / Math.log(2.0))) / ZOOM_MAX_POWER);

		// Set value; this will also trigger listeners
		zoomControl.setValue(n);
	}
	
	// Utility functions to pick out meaningful tick sizes
	private int findTickSizeIndex(long sz) {
		int i = 0;
		while (i < TICK_DIVISIONS.length && TICK_DIVISIONS[i] < sz) {
			i++;
		}
		return i;
	}

	private long findNamedTickSize() {
		long longest = 1;
		long duration = getEnd() - getStart();
		for (Long sz : NAMED_TICK_SIZES.keySet()) {
			if (sz < duration && sz > longest) {
				longest = sz;
			}
		}
		return longest;
	}
	
	private class TickMarkPanel extends JPanel {
		private static final long serialVersionUID = -3582412138892442244L;

		public void paintComponent (Graphics g) {
			int tickHeight = TICK_AREA_HEIGHT / 3;
			int tickBottom = tickHeight * 2;
			long largeTickSize = findNamedTickSize();
			int tickSizeIndex = findTickSizeIndex(largeTickSize);
			
			// Enable smooth rendering, since ticks might alias to pixels unevenly
			if (g instanceof Graphics2D) {
				RenderingHints renderHints = new RenderingHints(
						RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				renderHints.put(RenderingHints.KEY_RENDERING,
						RenderingHints.VALUE_RENDER_QUALITY);
				((Graphics2D) g).setRenderingHints(renderHints);
			}
			
			// Draw ticks
			long startTime = getStart();
			long endTime = getEnd();
			g.setColor(Color.BLACK);
			g.setFont(g.getFont().deriveFont(8.0f));
			FontMetrics metrics = g.getFontMetrics(g.getFont());
			int ascent = metrics.getAscent();
			while (tickSizeIndex >= 0 && tickHeight > 2) {
				long tickInterval = TICK_DIVISIONS[tickSizeIndex];
				
				// Don't bunch together too many ticks
				if (tickInterval * getPixelScale() < 2.0) break;
				
				long tickStart = (startTime - startTime % tickInterval) + tickInterval;				
				for (long tick = tickStart; tick < endTime; tick += tickInterval) {
					int x = (int) ((tick - startTime) * getPixelScale());
					g.drawLine(x, tickBottom - tickHeight, x, tickBottom);
					
					// Label largest tick marks only
					if (tickInterval == largeTickSize) {
						String tickNumber = Long.toString(tick / tickInterval);
						g.drawString(tickNumber, x - metrics.stringWidth(tickNumber) / 2, tickBottom + ascent + 2);
					}
				}
				
				
				// Shrink subsequent ticks
				tickHeight = (tickHeight * 3) / 4;
				tickSizeIndex--;
			}

			// Draw start/end labels for time
			String startText = DurationFormatter.formatDuration(startTime);
			String endText = DurationFormatter.formatDuration(endTime);
			g.drawString(startText, 0, TICK_AREA_HEIGHT / 3 - metrics.getDescent() - 2);
			g.drawString(endText, getWidth() - metrics.stringWidth(endText), TICK_AREA_HEIGHT / 3 - metrics.getDescent() - 2);
		}		
	}
	
	private class TimelineOverlay extends JComponent implements MouseListener, MouseMotionListener {
		private static final long serialVersionUID = -2681962764332227548L;
		private boolean isActive = false;
		private int x = 0;
		
		private List<Component> costComponents = new ArrayList<Component>();
		
		private void findCostComponentsIn(Container container) {
			if (container instanceof CostOverlay) {
				costComponents.add(container);
			}
			for (Component c : container.getComponents()) {
				if (c instanceof Container) {
					findCostComponentsIn((Container) c);
				}
			}
		}
		
		@Override
		public void mousePressed(MouseEvent e) {
			isActive = true;
			setVisible(true);
			x = e.getX();
			findCostComponentsIn(contentPane);
			repaint();
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			isActive = false;
			setVisible(false);
			costComponents.clear();
			repaint();
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			x = e.getX();
			repaint();
		}

		@Override
		public void mouseMoved(MouseEvent e) {
			x = e.getX();
			repaint();
		}

		private int getXRelativeToContentPane(Component c) {
			if (c == contentPane || c == null) {
				return 0;
			} else {
				return getXRelativeToContentPane(c.getParent()) + c.getX();
			}
		}
		
		private int getYRelativeToContentPane(Component c) {
			if (c == contentPane || c == null) {
				return 0;
			} else {
				return getYRelativeToContentPane(c.getParent()) + c.getY();
			}
		}
		
		public void paintComponent(Graphics g) {
			if (isActive) {
				g.setColor(OVERLAY_COLOR);
				g.fillRect(x + getLeftPadding(),0,1,getHeight());
				long time = (long) (x / getPixelScale()) + getTimeOffset();
				FontMetrics metrics = g.getFontMetrics(g.getFont());
				for (Component c : costComponents) {
					if (c instanceof CostOverlay && c.isShowing()) {
						int compX = getXRelativeToContentPane(c);
						if (compX <= x+getLeftPadding() && compX + c.getWidth() >= x+getLeftPadding()) {
							List<CostFunctionCapability> costs = ((CostOverlay) c).getCostFunctions();
							String costString = "";
							for (CostFunctionCapability cost : costs) {
								costString += FORMAT.format(cost.getValue(time)) + "" + cost.getUnits() + " ";
							}
							if (!costString.isEmpty()) {
								int leftX = x + getLeftPadding();
								int centerY = getYRelativeToContentPane(c) + c.getHeight() / 2;
								int width = metrics.stringWidth(costString);
								int height = metrics.getHeight() * 3 / 2;
								g.setColor(OVERLAY_COLOR);
								
								g.fillRect(leftX, centerY - height/2, width + 4, height);
								g.setColor(OVERLAY_TEXT_COLOR);								
								g.drawString(costString, leftX + 2, centerY + metrics.getAscent() / 2 - 1);
							}
						}
					}
				}
			}			
		}

		@Override
		public void mouseClicked(MouseEvent e) {
		}

		@Override
		public void mouseEntered(MouseEvent e) {
		}

		@Override
		public void mouseExited(MouseEvent e) {
		}
	}
	
	private class PanIcon implements Icon {
		private final int sign; 
		
		public PanIcon(int sign) {
			this.sign = sign;
		}
		
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			// Draw a triangle pointing either right or left
			int halfSize = getIconWidth() / 2;
			int fromCenter = halfSize - 2;
			int[] xPts = new int[]{ halfSize - fromCenter * sign, halfSize - fromCenter * sign, halfSize + fromCenter * sign};
			int[] yPts = new int[]{ halfSize - fromCenter, halfSize + fromCenter, halfSize };
			for (int i = 0; i < 3; i++) {
				xPts[i] += x;
				yPts[i] += y;
			}
			g.fillPolygon(xPts, yPts, 3);
		}

		@Override
		public int getIconWidth() {
			return PAN_ICON_SIZE;
		}

		@Override
		public int getIconHeight() {
			return PAN_ICON_SIZE;
		}
		
	}
	
	private class Panner implements ActionListener {
		private int sign;
		private long targetCenter;
		private long speed;
		private Timer timer;
		
		public Panner(int sign) {
			this.sign = sign;
		}
		
		// On button click
		@Override
		public void actionPerformed(ActionEvent e) {
			if (timer != null) {
				timer.stop();
			}

			long delta = sign * (getEnd() - getStart());
			targetCenter = centerTime + delta;
			
			// Clamp the target - make sure you don't go past left or right edge
			targetCenter = Math.max(Math.min(targetCenter, getMaximumCenter()), getMinimumCenter());
			speed = delta / (PAN_TIME / PAN_INTERVAL);

			timer = new Timer((int) PAN_INTERVAL, new ActionListener() {
				// On timer fires
				@Override
				public void actionPerformed(ActionEvent e) {
					centerTime += speed;
					speed = (long)  (speed * PAN_DRAG);
					if ((centerTime - targetCenter) / sign >= 0) {
						centerTime = targetCenter;
						timer.stop();
						timer = null;
					}
					if (speed * sign <= getPixelScale()) {
						timer.stop();
						timer = null;
					}
					stateChanged(new ChangeEvent(e.getSource()));
				}
			});
			timer.start();
		}

		

	}
	
	private class CompositePanZoomListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent evt) {
			float min = compositeControl.getLowProportion();
			float max = compositeControl.getHighProportion();
			long span = end - start;
			long minTime = start + (long) (span * min);
			long maxTime = start + (long) (span * max);			
			
			// Set the zoom; will also fire listeners to update
			setZoom( (double) span / (double) (maxTime-minTime)  );
			
			// Set the center time
			centerTime = (minTime + maxTime) / 2;
			stateChanged(new ChangeEvent(evt.getSource()));
		}
	}
	
	private class CompositeControlConnector extends JComponent {
		private static final long serialVersionUID = -2880528139468798708L;

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			g.setColor(getForeground());
			
			// Compute relevant pixel positions/sizes for connecting lines
			int edge = compositeControl.getEdgeWidth();
			int w = getWidth() - 1;
			int h = getHeight() / 2;
			int w2 = compositeControl.getWidth() - 1 - 2 * edge;
			int x1 = (int) (compositeControl.getLowProportion() * w2) + edge;
			int x2 = (int) (compositeControl.getHighProportion() * w2) + edge;
			
			// Draw connecting lines
			g.drawLine(0, 0, 0, h);
			g.drawLine(0, h, x1, h);
			g.drawLine(x1, h, x1, h*2);
			g.drawLine(w, 0, w, h);
			g.drawLine(w, h, x2, h);
			g.drawLine(x2, h, x2, h*2);
		}
		
	}
	
	/**
	 * Swing components which implement CostOverlay will be detected when the 
	 * user triggers cost overlay functionality; the CostFunctionCapabilities 
	 * they report will be used to construct a display of the total costs 
	 * associated with some component at some specific time. 
	 * 
	 * @author vwoeltje
	 *
	 */
	public static interface CostOverlay {
		public List<CostFunctionCapability> getCostFunctions();
	}

	public void addChangeListener(ChangeListener l) {
		changeListeners.add(l);
	}
	
	public void removeChangeListener(ChangeListener l) {
		changeListeners.remove(l);
	}

	@Override
	public void stateChanged(ChangeEvent e) {
		// Clamp the center time
		centerTime = Math.max(Math.min(centerTime, getMaximumCenter()), getMinimumCenter());
		updateCompositeControl();
		updateLabels();
		revalidate();
		repaint();
		contentPane.revalidate();
		contentPane.repaint();
		for (ChangeListener l : changeListeners) {
			l.stateChanged(e);
		}
	}
	
	@Override
	public void addSelectionChangeListener(PropertyChangeListener listener) {
		addPropertyChangeListener(SelectionProvider.SELECTION_CHANGED_PROP, listener);
	}

	@Override
	public void removeSelectionChangeListener(PropertyChangeListener listener) {
		removePropertyChangeListener(SelectionProvider.SELECTION_CHANGED_PROP, listener);
	}

	@Override
	public Collection<View> getSelectedManifestations() {		
		return (selectedView == null) ? Collections.<View>emptyList() : 
			Collections.<View>singleton(selectedView);
	}

	@Override
	public void clearCurrentSelections() {
		selectedView = null;
	}
	
	/**
	 * Select the specified view
	 * @param view the view to select
	 */
	public void select(View view) {
		if (controlParent != null && controlParent != this) {
			controlParent.select(view);
		} else {
			Collection<View> oldSelections = getSelectedManifestations();
			selectedView = view;
			firePropertyChange(SelectionProvider.SELECTION_CHANGED_PROP, oldSelections, getSelectedManifestations());
		}
	}
	
	/**
	 * Try to select a specific component (by id)
	 * If there is no embedded view of a component with this id, this
	 * method does nothing.
	 * 
	 * @param componentId the id of a component to select
	 */
	public void selectComponent(String componentId) {
		searchAndSelect(getContentPane(), componentId);
	}
	
	private void searchAndSelect(Component comp, String id) {
		if (comp instanceof View) {
			if (((View) comp).getManifestedComponent().getComponentId().equals(id)) {
				select((View) comp);
				return;
			}			
		}
		if (comp instanceof Container) { //Not found, keep searching
			for (Component child : ((Container) comp).getComponents()) {
				searchAndSelect(child, id);
			}
		}
	}
	
	

}
