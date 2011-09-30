/**
 * (C) 2010-2011 Alibaba Group Holding Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 
 * version 2 as published by the Free Software Foundation. 
 * 
 */


package com.taobao.datax.plugins.writer.mysqlwriter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.taobao.datax.common.exception.RerunableException;
import com.taobao.datax.common.plugin.LineReceiver;
import com.taobao.datax.common.plugin.ParamsKey;
import com.taobao.datax.common.plugin.PluginParam;
import com.taobao.datax.common.plugin.PluginStatus;
import com.taobao.datax.common.plugin.Writer;
import com.taobao.datax.common.util.DbSource;


public class MysqlWriter extends Writer {
	private static List<String> encodingConfigs = null;

	static {
		encodingConfigs = new ArrayList<String>();
		encodingConfigs.add("character_set_client");
		encodingConfigs.add("character_set_connection");
		encodingConfigs.add("character_set_database");
		encodingConfigs.add("character_set_results");
		encodingConfigs.add("character_set_server");
	}

	private static Map<String, String> encodingMaps = null;
	static {
		encodingMaps = new HashMap<String, String>();
		encodingMaps.put("utf-8", "UTF8");
	}

	private static final int MAX_ERROR_COUNT = 65535;

	private String username = null;

	private String password = null;

	private String host = null;

	private String port = null;

	private String dbname = null;

	private String table = null;

	private String colorder = null;

	private String pre = null;

	private String post = null;

	private String encoding = null;

	private char sep = '\001';

	private String set = "";

	private String replace = "IGNORE";

	private double limit = 0;

	private int lineCounter = 0;
	
	/* since load-data mechanisms only allowes one thread to load data */
	private int concurrency = 1;

	private String sourceUniqKey = "";

	private static String DRIVER_NAME = "com.mysql.jdbc.Driver";

	private Connection connection = null;

	private Logger logger = Logger.getLogger(MysqlWriter.class);
	

	@Override
	public int init() {
		this.username = param.getValue(ParamsKey.MysqlWriter.username, "");
		this.password = param.getValue(ParamsKey.MysqlWriter.password, "");
		this.host = param.getValue(ParamsKey.MysqlWriter.ip);
		this.port = param.getValue(ParamsKey.MysqlWriter.port, "3306");
		this.dbname = param.getValue(ParamsKey.MysqlWriter.dbname);
		this.table = param.getValue(ParamsKey.MysqlWriter.table);
		this.colorder = param.getValue(ParamsKey.MysqlWriter.colorder, "");
		this.pre = param.getValue(ParamsKey.MysqlWriter.pre, "");
		this.post = param.getValue(ParamsKey.MysqlWriter.post, "");
		this.encoding = param.getValue(ParamsKey.MysqlWriter.encoding, "UTF8").toLowerCase();
		this.limit = param.getDoubleValue(ParamsKey.MysqlWriter.limit, 0);
		this.set = param.getValue(ParamsKey.MysqlWriter.set, "");
		this.replace = param.getBoolValue(ParamsKey.MysqlWriter.replace, false) ? "REPLACE"
				: "IGNORE";

		this.sourceUniqKey = DbSource.genKey(this.getClass(), host, port, dbname);

		if (!StringUtils.isBlank(this.set)) {
			this.set = "set " + this.set;
		}

		if (encodingMaps.containsKey(this.encoding)) {
			this.encoding = encodingMaps.get(this.encoding);
		}

		return PluginStatus.SUCCESS.value();
	}

	@Override
	public int prepare(PluginParam param) {
		this.setParam(param);

		DbSource.register(this.sourceUniqKey, this.genProperties());

		/* we need to get inner connection */
		BasicDataSource source = (BasicDataSource) DbSource
				.getDataSource(this.sourceUniqKey);
		source.setAccessToUnderlyingConnectionAllowed(true);

		if (StringUtils.isBlank(this.pre))
			return PluginStatus.SUCCESS.value();


		Statement stmt = null;
		try {
			this.connection = DbSource.getConnection(this.sourceUniqKey);
			
			stmt = this.connection.createStatement(
					ResultSet.TYPE_SCROLL_INSENSITIVE,
					ResultSet.CONCUR_UPDATABLE);

			for (String subSql : this.pre.split(";")) {
				this.logger.info(String.format("Excute prepare sql %s .",
						subSql));
				stmt.execute(subSql);
			}

			return PluginStatus.SUCCESS.value();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RerunableException(e.getCause());
		} finally {
			try {
				if (null != stmt) {
					stmt.close();
				}
				if (null != this.connection) {
					this.connection.close();
					this.connection = null;
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public int post(PluginParam param) {
		if (StringUtils.isBlank(this.post))
			return PluginStatus.SUCCESS.value();

		/*	add by bazhen.csy 	
 		if (null == this.connection) {
			throw new RerunableException(String.format(
					"MysqlWriter connect %s failed in post work .", this.host));
		}*/

		Statement stmt = null;
		try {
			this.connection = DbSource.getConnection(this.sourceUniqKey);
			
			stmt = this.connection.createStatement(
					ResultSet.TYPE_SCROLL_INSENSITIVE,
					ResultSet.CONCUR_UPDATABLE);

			for (String subSql : this.post.split(";")) {
				this.logger.info(String.format("Excute prepare sql %s .",
						subSql));
				stmt.execute(subSql);
			}

			return PluginStatus.SUCCESS.value();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RerunableException(e.getCause());
		} finally {
			try {
				if (null != stmt) {
					stmt.close();
				}
				if (null != this.connection) {
					this.connection.close();
					this.connection = null;
				}
			} catch (Exception e2) {
				e2.printStackTrace();
			}

		}
	}

	@Override
	public int connectToDb() {
		return PluginStatus.SUCCESS.value();
	}

	@Override
	public int startWrite(LineReceiver receiver) {
		com.mysql.jdbc.Statement stmt = null;
		try {
			this.connection = ((org.apache.commons.dbcp.DelegatingConnection) DbSource
					.getConnection(this.sourceUniqKey)).getInnermostDelegate();
			
			stmt = (com.mysql.jdbc.Statement) this.connection.createStatement(
					ResultSet.TYPE_SCROLL_INSENSITIVE,
					ResultSet.CONCUR_UPDATABLE);

			/* set max count */
			this.logger.info(String.format(
					"Config max_error_count: set max_error_count=%d",
					this.MAX_ERROR_COUNT));
			stmt.executeUpdate(String.format("set max_error_count=%d;",
					this.MAX_ERROR_COUNT));

			/* set connect encoding */
			this.logger.info(String.format("Config encoding %s .",
					this.encoding));
			for (String sql : this.makeLoadEncoding(encoding))
				stmt.execute(sql);

			/* load data begin */
			String loadSql = this.makeLoadSql();
			this.logger
					.info(String.format("Load sql: %s.", visualSql(loadSql)));

			MysqlWriterInputStreamAdapter localInputStream = new MysqlWriterInputStreamAdapter(
					receiver, this);
			stmt.setLocalInfileInputStream(localInputStream);
			stmt.executeUpdate(visualSql(loadSql));
			this.lineCounter = localInputStream.getLineNumber();

			this.logger.info("DataX write to mysql ends .");

			return PluginStatus.SUCCESS.value();
		} catch (Exception e2) {
			e2.printStackTrace();
			throw new RerunableException(e2.getCause());
		} finally {
			if (null != stmt)
				try {
					stmt.close();
				} catch (SQLException e3) {
					e3.printStackTrace();
				}
		}
	}

	private String quoteData(String data) {
		if (data == null || data.trim().startsWith("@")
				|| data.trim().startsWith("`"))
			return data;
		return ('`' + data + '`');
	}

	private String visualSql(String sql) {
		Map<String, String> map = new HashMap<String, String>();
		map.put("\n", "\\n");
		map.put("\t", "\\t");
		map.put("\r", "\\r");
		map.put("\\", "\\\\");

		for (String s : map.keySet()) {
			sql = sql.replace(s, map.get(s));
		}
		return sql;
	}

	// colorder can not be null
	private String splitColumns(String colorder) {
		String[] columns = colorder.split(",");
		StringBuilder sb = new StringBuilder();
		for (String column : columns) {
			sb.append(quoteData(column.trim()) + ",");
		}
		return sb.substring(0, sb.lastIndexOf(","));
	}

	private String makeLoadSql() {
		String sql = "LOAD DATA LOCAL INFILE '`bazhen.csy.hedgehog`' "
				+ this.replace + " INTO TABLE ";
		// fetch table
		sql += this.quoteData(this.table);
		// fetch charset
		sql += " CHARACTER SET " + this.encoding;
		// fetch records
		sql += String.format(" FIELDS TERMINATED BY '\001' ESCAPED BY '\\' ");
		// sql += String.format(" FIELDS TERMINATED BY '%c' ", this.sep);
		// fetch lines
		sql += String.format(" LINES TERMINATED BY '\002' ");
		// fetch colorder
		if (this.colorder != null && !this.colorder.trim().isEmpty()) {
			sql += "(" + splitColumns(this.colorder) + ")";
		}
		// add set statement
		sql += this.set;
		sql += ";";
		return sql;
	}

	private List<String> makeLoadEncoding(String encoding) {
		List<String> ret = new ArrayList<String>();

		String configSql = "SET %s=%s; ";
		for (String config : encodingConfigs) {
			this.logger.info(String.format(configSql, config, encoding));
			ret.add(String.format(configSql, config, encoding));
		}

		return ret;
	}

	@Override
	public int commit() {
		return PluginStatus.SUCCESS.value();
	}

	@Override
	public int finish() {
		Statement stmt = null;
		try {
			StringBuilder sb = new StringBuilder();

			String PATTERN = "row \\d+";
			Pattern p = Pattern.compile(PATTERN);
			Set<String> rowCounter = new HashSet<String>();

			stmt = this.connection.createStatement(
					ResultSet.TYPE_SCROLL_INSENSITIVE,
					ResultSet.CONCUR_READ_ONLY);
			ResultSet rs = null;

			sb.setLength(0);
			rs = stmt.executeQuery("SHOW ERRORS;");
			while (rs.next()) {
				sb.append(rs.getString(1)).append(" ").append(rs.getInt(2))
						.append(" ").append(rs.getString(3)).append("\n");
			}

			if (!StringUtils.isBlank(sb.toString())) {
				this.logger.error(sb);
			}

			sb.setLength(0);
			sb.append('\n');
			rs = stmt.executeQuery("SHOW WARNINGS;");
			while (rs.next()) {
				sb.append(rs.getString(1)).append(" ").append(rs.getInt(2))
						.append(" ").append(rs.getString(3)).append("\n");
			}

			if (!StringUtils.isBlank(sb.toString())) {
				this.logger.warn(sb);

				// statistics
				Matcher matcher = p.matcher(sb.toString());
				while (matcher.find()) {
					rowCounter.add(matcher.group());
				}

				if (this.limit >= 1 && rowCounter.size() >= this.limit) {
					this.logger.error(String.format(
							"%d rows data failed in loading.",
							rowCounter.size()));
					return PluginStatus.FAILURE.value();
				} else if (this.limit > 0 && this.limit < 1
						&& this.lineCounter > 0) {
					double rate = (double) rowCounter.size()
							/ (double) this.lineCounter;
					if (rate >= this.limit) {
						this.logger.error(String.format(
								"%.1f%% data failed in loading.", rate * 100));
						return PluginStatus.FAILURE.value();
					}
				} else {
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (stmt != null)
					stmt.close();
				if (null != this.connection)
					this.connection.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return PluginStatus.SUCCESS.value();
	}

	private Properties genProperties() {
		Properties p = new Properties();
		p.setProperty("driverClassName", this.DRIVER_NAME);
		p.setProperty("url", String.format("jdbc:mysql://%s:%s/%s", this.host,
				this.port, this.dbname));
		p.setProperty("username", this.username);
		p.setProperty("password", this.password);
		p.setProperty("maxActive", String.valueOf(this.concurrency + 2));
		p.setProperty("maxWait", "3600");
		p.setProperty("removeAbandoned", "true");
		p.setProperty("removeAbandonedTimeout", "120");
		p.setProperty("testOnBorrow", "true");
		
		return p;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getDbname() {
		return dbname;
	}

	public void setDbname(String dbname) {
		this.dbname = dbname;
	}

	public String getTable() {
		return table;
	}

	public void setTable(String table) {
		this.table = table;
	}

	public String getColorder() {
		return colorder;
	}

	public void setColorder(String colorder) {
		this.colorder = colorder;
	}

	public String getPre() {
		return pre;
	}

	public void setPre(String pre) {
		this.pre = pre;
	}

	public String getPost() {
		return post;
	}

	public void setPost(String post) {
		this.post = post;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public double getLimit() {
		return limit;
	}

	public void setLimit(double limit) {
		this.limit = limit;
	}
	
	public char getSep() {
		return sep;
	}

	public void setSep(char sep) {
		this.sep = sep;
	}
}