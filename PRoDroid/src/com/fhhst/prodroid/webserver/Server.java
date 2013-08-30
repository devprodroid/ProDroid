/*
 * Copyright (C) 2009,2010 Markus Bode Internetösungen (bolutions.com)
 * 
 * Licensed under the GNU General Public License v3
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Markus Bode
 * @version $Id: Server.java 727 2011-01-02 13:04:32Z markus $
 */

package com.fhhst.prodroid.webserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class Server extends Thread {
	private ServerSocket listener = null;
	private static Handler mHandler;
	private boolean running = true;

	public static LinkedList<Socket> clientList = new LinkedList<Socket>();
	private String filesDir;
	public ProDroidServer proServer;

	public Server(String ip, int port, Handler handler, String filesDir, ProDroidServer aServer)
			throws IOException {
		super();
		mHandler = handler;
		this.filesDir = filesDir;
		this.proServer = aServer;
		InetAddress ipadr = InetAddress.getByName(ip);
		listener = new ServerSocket(port, 0, ipadr);
	}

	private static void send(String s) {
		Message msg = new Message();
		Bundle b = new Bundle();

		b.putString("msg", s);
		msg.setData(b);
		mHandler.sendMessage(msg);
	}

	@Override
	public void run() {
		while (running) {
			try {
				send("Waiting for connections.");
				Socket client = listener.accept();
				send("New connection from "
						+ client.getInetAddress().toString());
				new ServerHandler(client,filesDir,this).start();
				clientList.add(client);
			} catch (IOException e) {
				send(e.getMessage());
			}
		}
	}

	public void stopServer() {
		running = false;
		try {
			listener.close();
		} catch (IOException e) {
			send(e.getMessage());
		}
	}

	public synchronized static void remove(Socket s) {
		send("Closing connection: " + s.getInetAddress().toString());
		clientList.remove(s);
	}
}
