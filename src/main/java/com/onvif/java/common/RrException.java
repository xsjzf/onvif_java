package com.onvif.java.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自定义异常
 * @author zf
 */
public class RrException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private Logger logger = LoggerFactory.getLogger(getClass());
	
    private String msg;
    private int code = 500;
    
    public RrException(String msg) {
		super(msg);
		this.msg = msg;
	}
	
	public RrException(String msg, Throwable e) {
		super(msg, e);
		this.msg = msg;
		logger.error(e.getMessage(), e);
	}
	
	public RrException(String msg, int code) {
		super(msg);
		this.msg = msg;
		this.code = code;
	}
	
	public RrException(String msg, int code, Throwable e) {
		super(msg, e);
		this.msg = msg;
		this.code = code;
		logger.error(e.getMessage(), e);
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}
	
	
}
