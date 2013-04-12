/*******************************************************************************
 * Copyright (c) 2012 MD PnP Program.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package org.mdpnp.apps.gui.waveform;

import java.util.List;

public class WaveformRenderer {
	private WaveformSource source;
	
	private final List<WaveformSource> otherSources = new java.util.concurrent.CopyOnWriteArrayList<WaveformSource>();
	
	public WaveformRenderer(WaveformSource source) {
		this.source = source;
	}
	
	private int lastCount = -1;
	private WaveformCanvas.Extent extent;
	private int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
	
	private static final int incr(int x, int max) {
		return ++x>=max?0:x;
	}

	private static final int decr(int x, int max) {
		return --x<0?(max-1):x;
	}
	
	private static final int scaleX(int x, int minX0, int maxX0, int minX1, int maxX1) {
		double d = 1.0 * (x - minX0) / (maxX0 - minX0);
		// naive impl for now
		return minX1 + (int)(d * (maxX1 - minX1)); 
		
	}
	
	private final int scaleX(int x, int max) {
		int minX = extent.getMinX();
		int maxX = extent.getMaxX();
		return scaleX(x, 0, max, minX, maxX);
	}
	
	private final int scaleY(int y, int minY0, int maxY0) {
		int minY1 = extent.getMinY();
		int maxY1 = extent.getMaxY();
		return scaleX(y, minY0, maxY0, minY1, maxY1);
	}
	
	
	public void addOtherSource(int r, int g, int b, int a, WaveformSource source) {
		otherSources.add(source);
	}
	
	public static final class Rect {
		public int bottom, top, left, right;
	}
	
	private boolean continuousRescale = true;
	
	public void setContinuousRescale(boolean continuousRescale) {
		this.continuousRescale = continuousRescale;
	}
	
	private int minimumClearLines = 10;
	public void forceRescale() {
		lastCount = 0;
		minY = Integer.MAX_VALUE;
		maxY = Integer.MIN_VALUE;
	}
	
	public void render(WaveformCanvas canvas, Rect rect) {
		
		long start = System.currentTimeMillis();
		WaveformSource source = this.source;
		
		if(null == canvas || null == source) {
			return;
		}
		
//		System.out.println(source.getCount()+" ");
		
		WaveformCanvas.Extent extent = canvas.getExtent();

		int first = lastCount;
		int max = source.getMax();
		int last = decr(source.getCount(), max);
		
		if(!extent.equals(this.extent)) {
			first = 0;
			last = source.getMax() - 1;
			this.extent = extent;
		} else if(first == -1) {
			first = 0;
			last = source.getMax() - 1;
		}
		
		// TODO this is temporary 

		
		int height = extent.getMaxY() - extent.getMinY();
		int width = (extent.getMaxX() - extent.getMinX()) / (max - 1);

		rect.left = 0;
		rect.bottom = height;
		rect.right = width;
		rect.top = 0;
//		canvas.getExtent().
		
		int x = first;
		
		if(last >= 0) {
			while(x != last) {
				int x1 = incr(x, max);
				// TODO gain some efficiencies here
				int y = source.getValue(x);
				int y1 = source.getValue(x1);
	
				if(continuousRescale && x == (max-1)) {
					minY = Integer.MAX_VALUE;
					maxY = Integer.MIN_VALUE;
				}
				if(y1 < minY || y < minY) {
					minY = Math.min(y, y1);
					x = 0;
//					canvas.clearAll();
					continue;
				}
				if(y1 >= maxY || y >= maxY) {
					// max needs to be +1 from the highest point
					maxY = Math.max(y, y1) + 1;
					x = 0;
//					canvas.clearAll();
					continue;
				}
				
				// Don't draw the wraparound line (from max back to 0)
				if(x1 > x && x != x1) {
					int pixelRight = scaleX(x1, max) - scaleX(x, max) + minimumClearLines;
//					if(pixelRight>=width) {
//						pixelRight = width - 1;
//					}
					canvas.clearRect(scaleX(x, max)+1, scaleY(minY, minY, maxY), pixelRight, scaleY(maxY, minY, maxY)-scaleY(minY, minY, maxY));
					
//					int[] prevColor = canvas.getColor();
					for(WaveformSource cs : otherSources) {
						int _y = cs.getValue(x);
						int _y1 = cs.getValue(x1);
//						canvas.setColor(cs.r, cs.g, cs.b, cs.a);
						canvas.drawSecondaryLine(scaleX(x, max), scaleY(_y, minY, maxY), scaleX(x1, max), scaleY(_y1, minY, maxY));
					}
//					canvas.setColor(prevColor);
//					Log.d(WaveformRenderer.class.getName(), "x0="+x+",y0="+y+",x1="+x1+",y1="+y1+",minY="+minY+",maxY="+maxY); 
					canvas.drawLine(scaleX(x, max), scaleY(y, minY, maxY), scaleX(x1, max), scaleY(y1, minY, maxY));
					
				} else {
//					canvas.clearAll();
				}
				x = x1;
			}
		}
		lastCount = last;
//		lastCount = source.getCount();
		
//		System.out.println("Render time:" + (System.currentTimeMillis() - start) + "ms  count=" + source.getCount());
	}
	
}