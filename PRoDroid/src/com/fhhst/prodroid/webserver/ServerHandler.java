/*
 * Copyright (C) 2009,2010 Markus Bode Internetlösungen (bolutions.com)
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
 * @version $Id: ServerHandler.java 727 2011-01-02 13:04:32Z markus $
 */
package com.fhhst.prodroid.webserver;

import java.io.*;
import java.net.*;

class ServerHandler extends Thread {

	private PrintWriter out;
	private Socket toClient;
	private String mfilesDir;
	private int printProgress;

	ServerHandler(Socket s, String afilesDir, Server aServ) {
		toClient = s;
		this.mfilesDir = afilesDir;
		printProgress = aServ.proServer.printProgress;
	}

	public void run() {
		String dokument = "";

		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(
					toClient.getInputStream()));

			// Receive data
			while (true) {
				String s = in.readLine().trim();

				if (s.equals("")) {
					break;
				}

				if (s.substring(0, 3).equals("GET")) {
					int leerstelle = s.indexOf(" HTTP/");
					dokument = s.substring(5, leerstelle);
					dokument = dokument.replaceAll("[/]+", "/");
				}
			}
		} catch (Exception e) {
			Server.remove(toClient);
			try {
				toClient.close();
			} catch (Exception ex) {
			}
		}

		String header = "HTTP/1.1 %code%\n" + "Server: Bolutions/1\n"
				+ "Content-Length: %length%\n" + "Connection: close\n"
				+ "Content-Type: text/html; charset=iso-8859-1\n\n";

		try {
			// Send HTML-File (Ascii, not as a stream)

			out = new PrintWriter(toClient.getOutputStream(), true);
			out.print(header);

			out.print("<html><head><title>ProDroid Print Progress</title>");
			out.print("</head>");
			out.print("<body><a>Progress: " + String.valueOf(printProgress)
					+ " %" + "</a>.");

			// out.print(String.valueOf(printProgress)+"Prozent");

			out.print("</body>");

			out.flush();
			// }

			Server.remove(toClient);
			toClient.close();
		} catch (Exception e) {

		}
	}
}
