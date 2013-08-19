/*
 * Copyright (C) 2013 Robert Bremer
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
 * @author Robert Bremer

 */
package com.fhhst.prodroid.helper;

/**
 * @author robert
 * 			A class for helping find a sent gcodeline
 *
 */
public class GcodeLine {
	public String command;
	public long linenumber;

	public GcodeLine(long alinenumber, String aCommand) {

		if (!aCommand.isEmpty())
			command = aCommand;
		else
			throw new IllegalArgumentException("Command must not be empty");

		linenumber = alinenumber;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return command;
	}

}
