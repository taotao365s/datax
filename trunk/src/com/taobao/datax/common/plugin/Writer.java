/**
 * (C) 2010-2011 Alibaba Group Holding Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 
 * version 2 as published by the Free Software Foundation. 
 * 
 */


package com.taobao.datax.common.plugin;

import com.taobao.datax.common.exception.RerunableException;
import com.taobao.datax.common.exception.UnRerunableException;
import com.taobao.datax.engine.plugin.DefaultPlugin;
import com.taobao.datax.plugins.writer.streamwriter.StreamWriter;


/**
 * A kind of {@link Plugin} which dump data to data destination(e.g mysql, HDFS).
 * 
 * @see {@link Plugin}
 * @see {@link Reader}
 * 
 * */
public abstract class Writer extends DefaultPlugin{
	
	/**
	 * Initialize {@link Writer} before the Writer work.
	 * 
	 * @return
	 *			0 for OK, others for failure.
	 * 
	 * @throws	{@link	RerunableException}
	 * 					Method init failed, rerun DataX may resolve this problem, e.g. connect to database interrupted.
	 *					{@link UnRerunableException}
	 *					Method init failed, rerun DataX may resolve this problem, e.g. job configuration file format error.
	 *
	 * */
	public abstract int init();
	
	/**
	 * Connect to destination DB(e.g mysql, HDFS)
	 * 
	 * @return
	 *			0 for OK, others for failure.
	 * 
	 * @throws	{@link	RerunableException}
	 * 			Method connectToDb failed, rerun DataX may resolve this problem, e.g. connect to database interrupted.
	 *			{@link UnRerunableException}
	 *			Method connectToDb failed, rerun DataX may resolve this problem, e.g. job configuration file format error.
	 * */
	public abstract int connectToDb();
	
	/**
	 * Start to dump data into data destination.
	 * 
	 * @param	resultHandler	
	 * 			handler used by {@link Writer} to dump data from DataX engine(Usually in memory).
	 * 
	 * @return
	 *			0 for OK, others for failure.
	 * 
	 * @throws	{@link	RerunableException}
	 * 			Method startWrite failed, rerun DataX may resolve this problem, e.g. connect to database interrupted.
	 *			{@link UnRerunableException}
	 *			Method startWrite failed, rerun DataX may resolve this problem, e.g. job configuration file format error.
	 *
	 * */
	public abstract int startWrite(LineReceiver receiver);
	
	/**
	 * Commit transaction. A complement of method startDump.
	 * 
	 * @return
	 *			0 for OK, others for failure.
	 * 
	 * @throws	{@link	RerunableException}
	 * 			Method commit failed, rerun DataX may resolve this problem, e.g. connect to database interrupted.
	 *			{@link UnRerunableException}
	 *			Method commit failed, rerun DataX may resolve this problem, e.g. job configuration file format error.
	 * */
	public abstract int commit();
	
	
	/**
	 * Do some finish work
	 * 
	 * @return
	 *			0 for OK, others for failure.
	 * 
	 * @throws	{@link	RerunableException}
	 * 			Method finish failed, rerun DataX may resolve this problem, e.g. connect to database interrupted.
	 *			{@link UnRerunableException}
	 *			Method finish failed, rerun DataX may resolve this problem, e.g. job configuration file format error.
	 * */
	public abstract int finish();
}