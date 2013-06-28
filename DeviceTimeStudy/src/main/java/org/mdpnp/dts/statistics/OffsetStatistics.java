package org.mdpnp.dts.statistics;

/**
 * 
 * @author dalonso@mdpnp.org
 * <p>
 * Interface to define the statistics we need to get from our offsets
 *
 */
public interface OffsetStatistics {
	
	/**
	 * Returns the number of devices studied
	 * @return
	 */
	public int getCount();
	
	/**
	 * Returns the average offset of the devices (in miliseconds)
	 * @return
	 */
	public double getAvgOffset();
	
	/**
	 * Returns the average offset of the devices (in miliseconds), considering new minute (XX:00) 
	 * for those that don't display seconds
	 * @return
	 */
	public double getAvgOffsetNewMinute();
	
	/**
	 * Returns the avg offset of the devices (in miliseconds), considering that those who don't display 
	 * seconds are in the 'new minute eve' (XX:59)
	 * @return
	 */
	public double getAvgOffsetNewMinuteEve();
	
	/**
	 * Returns the standard deviation of the offsets (in miliseconds)
	 * @return
	 */
	public double getStdDev();
	
	/**
	 * Returns the minimal offset (in miliseconds)
	 * @return
	 */
	public long getMinOffset();
	
	/**
	 * Returns the maximal offset (in miliseconds)
	 * @return
	 */
	public long getMaxOffset();

}
