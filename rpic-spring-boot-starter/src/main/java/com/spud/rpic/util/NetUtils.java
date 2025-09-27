package com.spud.rpic.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Spud
 * @date 2025/2/15
 */
public class NetUtils {

	public static String getLocalHost() {
		try {
			return InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			return "127.0.0.1";
		}
	}

}